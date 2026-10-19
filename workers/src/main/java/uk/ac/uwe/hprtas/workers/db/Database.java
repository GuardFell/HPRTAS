package uk.ac.uwe.hprtas.workers.db;

import uk.ac.uwe.hprtas.workers.Config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The hospital's own record store.
 *
 * The case describes a Hospital Patient Administration System that becomes the system of record for
 * the pathway (AS-01, AS-02). This is that store: the patient register, the referrals, the
 * appointments, the payment and refund ledger, the dispatches and the missing-information requests
 * are persisted here rather than being held in the memory of whatever happened to be running.
 *
 * It is an embedded H2 database, which is the database Camunda 8 Run itself keeps its data in, so
 * the prototype stays on one database family. The file it lives in is configuration
 * ({@code database.url}); the unit tests point at a throwaway in-memory one instead.
 *
 * One connection serves every worker thread, and every operation takes the instance's lock. That is
 * the same discipline the simulated services used when their ledgers were maps: a check and the
 * write that follows it have to be one step, or two jobs can both find a reference unpaid and both
 * take money. H2 would give the same guarantee per statement, but a single connection makes the
 * transaction boundary explicit and keeps the read-modify-write sequences honest.
 */
public final class Database implements AutoCloseable {

  /** A patient as the hospital's register holds them. */
  public record Patient(
      String patientId,
      String nhsNumber,
      String patientName,
      String channelPreference,
      String firstSeen,
      String lastUpdated,
      int referrals) {}

  /** A referral as the hospital holds it. */
  public record Referral(
      String referralId,
      String patientId,
      String nhsNumber,
      String referringOrganisation,
      String receivedDate,
      Boolean documentsComplete,
      String missingItems,
      String validationResult,
      String lastUpdated) {}

  /** An appointment as the hospital holds it. */
  public record Appointment(
      String bookingKey,
      String appointmentType,
      String patientId,
      String referralId,
      String appointmentDate,
      String alternativeDate,
      Boolean withinRequestedWindow,
      String appointmentReference,
      String status,
      String outcome,
      int attempts,
      String lastUpdated) {}

  /** A payment transaction as the Payment Service Provider reported it. */
  public record Payment(
      String paymentReference,
      Number amount,
      String status,
      String transactionReference,
      String paymentDate,
      boolean confirmationReceived,
      boolean requiresInvestigation,
      String lastUpdated) {}

  /** A refund recorded against a settled payment. */
  public record Refund(
      String paymentReference,
      String refundReference,
      String refundDate,
      Number refundedAmount,
      String refundReason,
      String refundDecision,
      String lastUpdated) {}

  /** A letter or appointment notification the correspondence service dispatched. */
  public record Dispatch(
      String dispatchReference,
      String recipients,
      String dispatchChannel,
      String documentType,
      String dispatchDate,
      String patientId) {}

  /** What was asked of the referring organisation, and whether it has been supplied. */
  public record InformationRequest(
      String referralId,
      String requestedItems,
      String requestedFrom,
      String requestDate,
      String status,
      String suppliedDate,
      String lastUpdated) {}

  /** The tables, as the command line reports them. */
  public record Report(List<String> tables) {}

  private static final List<String> REPORTED_TABLES =
      List.of(
          "patients",
          "referrals",
          "appointments",
          "payments",
          "refunds",
          "dispatches",
          "information_requests");

  private final Connection connection;

  private Database(Connection connection) {
    this.connection = connection;
  }

  /**
   * Opens the database and makes sure the schema is there.
   *
   * A file database has its parent directory created, because H2 will not: the first start on a
   * fresh checkout has to work without a man-made directory.
   */
  public static Database open(Config.DatabaseSettings settings) {
    createParentDirectory(settings.url());
    try {
      final Connection connection =
          DriverManager.getConnection(settings.url(), settings.user(), settings.password());
      final Database database = new Database(connection);
      database.createSchema();
      return database;
    } catch (SQLException e) {
      throw new IllegalStateException(
          "the record store at " + settings.url() + " could not be opened (" + e.getMessage() + ")",
          e);
    }
  }

  private static void createParentDirectory(String url) {
    final String prefix = "jdbc:h2:file:";
    if (!url.startsWith(prefix)) {
      return;
    }
    final String withoutOptions = url.substring(prefix.length()).split(";", 2)[0];
    final Path file = Path.of(withoutOptions).toAbsolutePath();
    final Path parent = file.getParent();
    if (parent != null) {
      try {
        Files.createDirectories(parent);
      } catch (java.io.IOException e) {
        throw new IllegalStateException("the record store directory could not be created: " + parent, e);
      }
    }
  }

  /** The schema. Creating it is idempotent, so every start is safe. */
  private void createSchema() throws SQLException {
    final String[] statements = {
      "CREATE TABLE IF NOT EXISTS patients ("
          + " patient_id VARCHAR(64) PRIMARY KEY,"
          + " nhs_number VARCHAR(10),"
          + " patient_name VARCHAR(160),"
          + " channel_preference VARCHAR(40),"
          + " first_seen VARCHAR(10) NOT NULL,"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE UNIQUE INDEX IF NOT EXISTS patients_nhs_number ON patients (nhs_number)",
      "CREATE TABLE IF NOT EXISTS referrals ("
          + " referral_id VARCHAR(64) PRIMARY KEY,"
          + " patient_id VARCHAR(64),"
          + " nhs_number VARCHAR(10),"
          + " referring_organisation VARCHAR(160),"
          + " received_date VARCHAR(10),"
          + " documents_complete BOOLEAN,"
          + " missing_items VARCHAR(2000),"
          + " validation_result VARCHAR(40),"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS appointments ("
          + " booking_key VARCHAR(160) PRIMARY KEY,"
          + " appointment_type VARCHAR(40) NOT NULL,"
          + " patient_id VARCHAR(64),"
          + " referral_id VARCHAR(64),"
          + " appointment_date VARCHAR(10),"
          + " alternative_date VARCHAR(10),"
          + " within_requested_window BOOLEAN,"
          + " appointment_reference VARCHAR(80),"
          + " status VARCHAR(20) NOT NULL,"
          + " outcome VARCHAR(20),"
          + " attempts INT NOT NULL,"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS payments ("
          + " payment_reference VARCHAR(80) PRIMARY KEY,"
          + " amount DECIMAL(12,2),"
          + " status VARCHAR(20) NOT NULL,"
          + " transaction_reference VARCHAR(80),"
          + " payment_date VARCHAR(10),"
          + " confirmation_received BOOLEAN NOT NULL,"
          + " requires_investigation BOOLEAN NOT NULL,"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS refunds ("
          + " payment_reference VARCHAR(80) PRIMARY KEY,"
          + " refund_reference VARCHAR(80),"
          + " refund_date VARCHAR(10),"
          + " refunded_amount DECIMAL(12,2),"
          + " refund_reason VARCHAR(160),"
          + " refund_decision VARCHAR(20),"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS dispatches ("
          + " dispatch_reference VARCHAR(80) PRIMARY KEY,"
          + " recipients VARCHAR(2000),"
          + " dispatch_channel VARCHAR(40),"
          + " document_type VARCHAR(60),"
          + " dispatch_date VARCHAR(10) NOT NULL,"
          + " patient_id VARCHAR(64))",
      "CREATE TABLE IF NOT EXISTS information_requests ("
          + " referral_id VARCHAR(64) PRIMARY KEY,"
          + " requested_items VARCHAR(2000),"
          + " requested_from VARCHAR(160),"
          + " request_date VARCHAR(10),"
          + " status VARCHAR(20) NOT NULL,"
          + " supplied_date VARCHAR(10),"
          + " last_updated VARCHAR(10) NOT NULL)",
      "CREATE TABLE IF NOT EXISTS reference_sequence ("
          + " name VARCHAR(40) PRIMARY KEY,"
          // value is a reserved word in H2 2.x, so the column is named for what it holds
          + " next_value INT NOT NULL)"
    };
    synchronized (this) {
      try (Statement statement = connection.createStatement()) {
        for (String sql : statements) {
          statement.execute(sql);
        }
      }
    }
  }

  private static String today(Instant now) {
    return now.toString().substring(0, 10);
  }

  // ---------------------------------------------------------------- patients

  /** Looks a patient up by the hospital's own identifier. */
  public synchronized Optional<Patient> findPatient(String patientId) {
    if (patientId == null || patientId.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM patients WHERE patient_id = ?", patientId, this::readPatient);
  }

  /**
   * Looks a patient up by their NHS number.
   *
   * This is the check the case asks for when information arrives from another organisation (BR-46,
   * NFR-004): the number is the identifier the referring organisation knows, and the register is what
   * says whether this patient is already known to the hospital.
   */
  public synchronized Optional<Patient> findPatientByNhsNumber(String nhsNumber) {
    if (nhsNumber == null || nhsNumber.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM patients WHERE nhs_number = ?", nhsNumber, this::readPatient);
  }

  /** Inserts the patient, or updates the details the register already holds for them. */
  public synchronized void savePatient(Patient patient) {
    update(
        "MERGE INTO patients (patient_id, nhs_number, patient_name, channel_preference, first_seen,"
            + " last_updated) KEY (patient_id) VALUES (?, ?, ?, ?, COALESCE((SELECT first_seen FROM"
            + " patients WHERE patient_id = ?), ?), ?)",
        patient.patientId(),
        patient.nhsNumber(),
        patient.patientName(),
        patient.channelPreference(),
        patient.patientId(),
        patient.firstSeen(),
        patient.lastUpdated());
  }

  /** How many referrals the register already holds for a patient. */
  public synchronized int referralCount(String patientId) {
    if (patientId == null || patientId.isEmpty()) {
      return 0;
    }
    return scalar("SELECT COUNT(*) FROM referrals WHERE patient_id = ?", patientId);
  }

  // --------------------------------------------------------------- referrals

  public synchronized Optional<Referral> findReferral(String referralId) {
    if (referralId == null || referralId.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM referrals WHERE referral_id = ?", referralId, this::readReferral);
  }

  public synchronized void saveReferral(Referral referral) {
    update(
        "MERGE INTO referrals (referral_id, patient_id, nhs_number, referring_organisation,"
            + " received_date, documents_complete, missing_items, validation_result, last_updated)"
            + " KEY (referral_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        referral.referralId(),
        referral.patientId(),
        referral.nhsNumber(),
        referral.referringOrganisation(),
        referral.receivedDate(),
        referral.documentsComplete(),
        referral.missingItems(),
        referral.validationResult(),
        referral.lastUpdated());
  }

  // ------------------------------------------------------------ appointments

  public synchronized Optional<Appointment> findAppointment(String bookingKey) {
    if (bookingKey == null || bookingKey.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM appointments WHERE booking_key = ?", bookingKey, this::readAppointment);
  }

  public synchronized void saveAppointment(Appointment appointment) {
    update(
        "MERGE INTO appointments (booking_key, appointment_type, patient_id, referral_id,"
            + " appointment_date, alternative_date, within_requested_window, appointment_reference,"
            + " status, outcome, attempts, last_updated) KEY (booking_key)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        appointment.bookingKey(),
        appointment.appointmentType(),
        appointment.patientId(),
        appointment.referralId(),
        appointment.appointmentDate(),
        appointment.alternativeDate(),
        appointment.withinRequestedWindow(),
        appointment.appointmentReference(),
        appointment.status(),
        appointment.outcome(),
        appointment.attempts(),
        appointment.lastUpdated());
  }

  /**
   * Marks a patient's other live appointments as superseded.
   *
   * A rescheduled booking is a new booking for the same patient, and the appointment it replaces must
   * not stay on the register as though it were still going to happen. The row is kept rather than
   * deleted: the hospital has to be able to see that it existed and what happened to it.
   *
   * What counts as "the same booking" is the caller's to say. A patient appointment belongs to a
   * referral, so the scheduling service supersedes the same patient's live appointments for that
   * referral; a treatment appointment is identified by its type. A criterion that is null is not used.
   */
  public synchronized int supersedeActiveAppointments(
      String patientId,
      String referralId,
      String appointmentType,
      String exceptBookingKey,
      Instant now) {
    if (patientId == null || patientId.isEmpty()) {
      return 0;
    }
    final StringBuilder sql =
        new StringBuilder(
            "UPDATE appointments SET status = 'superseded', last_updated = ?"
                + " WHERE patient_id = ? AND booking_key <> ?"
                + " AND status IN ('booked', 'confirmed')");
    final List<Object> parameters = new ArrayList<>();
    parameters.add(today(now));
    parameters.add(patientId);
    parameters.add(exceptBookingKey);
    if (referralId != null && !referralId.isEmpty()) {
      sql.append(" AND referral_id = ?");
      parameters.add(referralId);
    }
    if (appointmentType != null && !appointmentType.isEmpty()) {
      sql.append(" AND appointment_type = ?");
      parameters.add(appointmentType);
    }
    return update(sql.toString(), parameters.toArray());
  }

  public synchronized List<Appointment> appointments() {
    return many("SELECT * FROM appointments ORDER BY booking_key", this::readAppointment);
  }

  // ------------------------------------------------------------------- money

  public synchronized Optional<Payment> findPayment(String paymentReference) {
    if (paymentReference == null || paymentReference.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM payments WHERE payment_reference = ?", paymentReference, this::readPayment);
  }

  public synchronized void savePayment(Payment payment) {
    update(
        "MERGE INTO payments (payment_reference, amount, status, transaction_reference, payment_date,"
            + " confirmation_received, requires_investigation, last_updated) KEY (payment_reference)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        payment.paymentReference(),
        toDecimal(payment.amount()),
        payment.status(),
        payment.transactionReference(),
        payment.paymentDate(),
        payment.confirmationReceived(),
        payment.requiresInvestigation(),
        payment.lastUpdated());
  }

  public synchronized Optional<Refund> findRefund(String paymentReference) {
    if (paymentReference == null || paymentReference.isEmpty()) {
      return Optional.empty();
    }
    return one("SELECT * FROM refunds WHERE payment_reference = ?", paymentReference, this::readRefund);
  }

  public synchronized void saveRefund(Refund refund) {
    update(
        "MERGE INTO refunds (payment_reference, refund_reference, refund_date, refunded_amount,"
            + " refund_reason, refund_decision, last_updated) KEY (payment_reference)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        refund.paymentReference(),
        refund.refundReference(),
        refund.refundDate(),
        toDecimal(refund.refundedAmount()),
        refund.refundReason(),
        refund.refundDecision(),
        refund.lastUpdated());
  }

  /**
   * The next value of a named reference sequence, taken and advanced in one step.
   *
   * The provider's reference numbers used to come from a counter in the worker's memory, which
   * restarted at one every time the process did. A sequence in the store means no two references the
   * provider issues are ever the same, across restarts.
   */
  public synchronized int nextReferenceNumber(String name) {
    final Integer current =
        one("SELECT next_value FROM reference_sequence WHERE name = ?", name,
            result -> result.getInt("next_value"))
            .orElse(null);
    final int next = (current == null ? 0 : current) + 1;
    update(
        "MERGE INTO reference_sequence (name, next_value) KEY (name) VALUES (?, ?)", name, next);
    return next;
  }

  // ------------------------------------------------------------- correspondence

  public synchronized void saveDispatch(Dispatch dispatch) {
    update(
        "MERGE INTO dispatches (dispatch_reference, recipients, dispatch_channel, document_type,"
            + " dispatch_date, patient_id) KEY (dispatch_reference) VALUES (?, ?, ?, ?, ?, ?)",
        dispatch.dispatchReference(),
        dispatch.recipients(),
        dispatch.dispatchChannel(),
        dispatch.documentType(),
        dispatch.dispatchDate(),
        dispatch.patientId());
  }

  public synchronized int dispatchCount() {
    return scalar("SELECT COUNT(*) FROM dispatches", new Object[0]);
  }

  // ------------------------------------------------------- information requests

  public synchronized void saveInformationRequest(InformationRequest request) {
    update(
        "MERGE INTO information_requests (referral_id, requested_items, requested_from, request_date,"
            + " status, supplied_date, last_updated) KEY (referral_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
        request.referralId(),
        request.requestedItems(),
        request.requestedFrom(),
        request.requestDate(),
        request.status(),
        request.suppliedDate(),
        request.lastUpdated());
  }

  public synchronized Optional<InformationRequest> findInformationRequest(String referralId) {
    if (referralId == null || referralId.isEmpty()) {
      return Optional.empty();
    }
    return one(
        "SELECT * FROM information_requests WHERE referral_id = ?",
        referralId,
        this::readInformationRequest);
  }

  /** Records that the referring organisation answered, which is an update to a stored record. */
  public synchronized int markDocumentsSupplied(String referralId, Instant now) {
    if (referralId == null || referralId.isEmpty()) {
      return 0;
    }
    return update(
        "UPDATE information_requests SET status = 'supplied', supplied_date = ?, last_updated = ?"
            + " WHERE referral_id = ?",
        today(now),
        today(now),
        referralId);
  }

  // ------------------------------------------------------------------ report

  /** The tables and their row counts, for the command line. */
  public synchronized Report report() {
    final List<String> tables = new ArrayList<>();
    for (String table : REPORTED_TABLES) {
      tables.add(table + " (" + scalar("SELECT COUNT(*) FROM " + table, new Object[0]) + " rows)");
    }
    return new Report(tables);
  }

  /**
   * The recorded rows, rendered one line each, for the command line.
   *
   * A demonstration ends by showing what the run left behind, and that has to be read from the store
   * rather than from anything the process still holds: the point of the store is that the records
   * outlive the instance as much as the worker process.
   */
  public synchronized List<String> rows() {
    final List<String> lines = new ArrayList<>();
    for (String table : REPORTED_TABLES) {
      final List<String> rendered = new ArrayList<>();
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT * FROM " + table)) {
        final java.sql.ResultSetMetaData metadata = result.getMetaData();
        while (result.next()) {
          final StringBuilder line = new StringBuilder();
          for (int column = 1; column <= metadata.getColumnCount(); column++) {
            if (column > 1) {
              line.append(", ");
            }
            line.append(metadata.getColumnLabel(column)).append('=').append(result.getString(column));
          }
          rendered.add(line.toString());
        }
      } catch (SQLException e) {
        throw new IllegalStateException("the record store rejected a read: " + e.getMessage(), e);
      }
      if (rendered.isEmpty()) {
        continue;
      }
      lines.add(table + ":");
      for (String row : rendered) {
        lines.add("  " + row);
      }
    }
    return lines;
  }

  // -------------------------------------------------------------------- rows

  private Patient readPatient(ResultSet result) throws SQLException {
    final String patientId = result.getString("patient_id");
    return new Patient(
        patientId,
        result.getString("nhs_number"),
        result.getString("patient_name"),
        result.getString("channel_preference"),
        result.getString("first_seen"),
        result.getString("last_updated"),
        referenceCountOf(patientId));
  }

  private int referenceCountOf(String patientId) {
    return scalar("SELECT COUNT(*) FROM referrals WHERE patient_id = ?", patientId);
  }

  private Referral readReferral(ResultSet result) throws SQLException {
    return new Referral(
        result.getString("referral_id"),
        result.getString("patient_id"),
        result.getString("nhs_number"),
        result.getString("referring_organisation"),
        result.getString("received_date"),
        nullableBoolean(result, "documents_complete"),
        result.getString("missing_items"),
        result.getString("validation_result"),
        result.getString("last_updated"));
  }

  private Appointment readAppointment(ResultSet result) throws SQLException {
    return new Appointment(
        result.getString("booking_key"),
        result.getString("appointment_type"),
        result.getString("patient_id"),
        result.getString("referral_id"),
        result.getString("appointment_date"),
        result.getString("alternative_date"),
        nullableBoolean(result, "within_requested_window"),
        result.getString("appointment_reference"),
        result.getString("status"),
        result.getString("outcome"),
        result.getInt("attempts"),
        result.getString("last_updated"));
  }

  private Payment readPayment(ResultSet result) throws SQLException {
    return new Payment(
        result.getString("payment_reference"),
        fromDecimal(result.getBigDecimal("amount")),
        result.getString("status"),
        result.getString("transaction_reference"),
        result.getString("payment_date"),
        result.getBoolean("confirmation_received"),
        result.getBoolean("requires_investigation"),
        result.getString("last_updated"));
  }

  private Refund readRefund(ResultSet result) throws SQLException {
    return new Refund(
        result.getString("payment_reference"),
        result.getString("refund_reference"),
        result.getString("refund_date"),
        fromDecimal(result.getBigDecimal("refunded_amount")),
        result.getString("refund_reason"),
        result.getString("refund_decision"),
        result.getString("last_updated"));
  }

  private InformationRequest readInformationRequest(ResultSet result) throws SQLException {
    return new InformationRequest(
        result.getString("referral_id"),
        result.getString("requested_items"),
        result.getString("requested_from"),
        result.getString("request_date"),
        result.getString("status"),
        result.getString("supplied_date"),
        result.getString("last_updated"));
  }

  private static Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
    final boolean value = result.getBoolean(column);
    return result.wasNull() ? null : value;
  }

  /** An amount as the column holds it. A null amount stays null rather than becoming zero. */
  private static BigDecimal toDecimal(Number amount) {
    if (amount == null) {
      return null;
    }
    return new BigDecimal(amount.toString());
  }

  /**
   An amount as the caller sent it.
   A whole number comes back whole - the amounts in this system are pounds, and a worker that
   returned 250 as an integer should read back as 250 rather than 250.0 - and a fractional one comes
   back as a decimal.
   */
  private static Number fromDecimal(BigDecimal value) {
    if (value == null) {
      return null;
    }
    final BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() <= 0) {
      final BigInteger whole = stripped.toBigIntegerExact();
      if (whole.bitLength() < 31) {
        return whole.intValue();
      }
      return whole.longValue();
    }
    return stripped.doubleValue();
  }

  // --------------------------------------------------------------------- sql

  private int update(String sql, Object... parameters) {
    synchronized (this) {
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        bind(statement, parameters);
        return statement.executeUpdate();
      } catch (SQLException e) {
        throw new IllegalStateException("the record store rejected a write: " + e.getMessage(), e);
      }
    }
  }

  private int scalar(String sql, Object... parameters) {
    return one(sql, parameters, result -> result.getInt(1)).orElse(0);
  }

  private <T> Optional<T> one(String sql, Object parameter, RowReader<T> reader) {
    return one(sql, new Object[] {parameter}, reader);
  }

  private synchronized <T> Optional<T> one(String sql, Object[] parameters, RowReader<T> reader) {
    synchronized (this) {
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        bind(statement, parameters);
        try (ResultSet result = statement.executeQuery()) {
          if (!result.next()) {
            return Optional.empty();
          }
          return Optional.of(reader.read(result));
        }
      } catch (SQLException e) {
        throw new IllegalStateException("the record store rejected a read: " + e.getMessage(), e);
      }
    }
  }

  private synchronized <T> List<T> many(String sql, RowReader<T> reader) {
    synchronized (this) {
      try (PreparedStatement statement = connection.prepareStatement(sql);
          ResultSet result = statement.executeQuery()) {
        final List<T> rows = new ArrayList<>();
        while (result.next()) {
          rows.add(reader.read(result));
        }
        return rows;
      } catch (SQLException e) {
        throw new IllegalStateException("the record store rejected a read: " + e.getMessage(), e);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
    for (int i = 0; i < parameters.length; i++) {
      final Object parameter = parameters[i];
      if (parameter == null) {
        statement.setObject(i + 1, null);
      } else if (parameter instanceof Boolean flag) {
        statement.setBoolean(i + 1, flag);
      } else if (parameter instanceof Integer number) {
        statement.setInt(i + 1, number);
      } else if (parameter instanceof Number number) {
        statement.setBigDecimal(i + 1, toDecimal(number));
      } else {
        statement.setString(i + 1, String.valueOf(parameter));
      }
    }
  }

  /** Reads one row into a record. */
  @FunctionalInterface
  private interface RowReader<T> {
    T read(ResultSet result) throws SQLException;
  }

  @Override
  public synchronized void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new IllegalStateException("the record store could not be closed: " + e.getMessage(), e);
    }
  }
}
