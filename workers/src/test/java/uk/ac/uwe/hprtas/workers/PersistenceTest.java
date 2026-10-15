package uk.ac.uwe.hprtas.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.uwe.hprtas.workers.db.Database;
import uk.ac.uwe.hprtas.workers.services.Services;
import uk.ac.uwe.hprtas.workers.workers.AppointmentAvailability;
import uk.ac.uwe.hprtas.workers.workers.CorrespondenceDispatch;
import uk.ac.uwe.hprtas.workers.workers.DocumentReceipt;
import uk.ac.uwe.hprtas.workers.workers.PaymentProcessing;
import uk.ac.uwe.hprtas.workers.workers.ReferralDocumentRequest;
import uk.ac.uwe.hprtas.workers.workers.ReferralValidation;

/**
 * Tests for the hospital's record store.
 *
 * The two things these check are the two the project was told were missing: that records are
 * persisted rather than held in the memory of whatever happened to be running (so they outlive the
 * worker process), and that a process retrieves and uses what is already recorded rather than asking
 * for it again. The scenario each test belongs to is named where one exists in
 * {@code tests/test-plan.md}.
 */
class PersistenceTest {

  private static final Config CONFIG = TestSupport.buildConfig();

  private static final ReferralValidation REFERRAL_VALIDATION = new ReferralValidation();
  private static final ReferralDocumentRequest REFERRAL_DOCUMENT_REQUEST =
      new ReferralDocumentRequest();
  private static final DocumentReceipt DOCUMENT_RECEIPT = new DocumentReceipt();
  private static final AppointmentAvailability APPOINTMENT_AVAILABILITY =
      new AppointmentAvailability();
  private static final PaymentProcessing PAYMENT_PROCESSING = new PaymentProcessing();
  private static final CorrespondenceDispatch CORRESPONDENCE_DISPATCH = new CorrespondenceDispatch();

  private static Map<String, Object> completed(Outcome outcome) {
    assertEquals("completed", outcome.kind(), "the handler did not complete: " + outcome.kind());
    return ((Outcome.Completed) outcome).variables();
  }

  private static void seedPatient(
      Database database, String patientId, String nhsNumber, String name, String preference) {
    database.savePatient(
        new Database.Patient(
            patientId, nhsNumber, name, preference, "2026-01-01", "2026-01-01", 0));
  }

  @Test
  @DisplayName("the store outlives the worker process: a settled payment is still known after a restart (DEF-10, AC-10)")
  void recordsSurviveARestart(@TempDir Path directory) {
    final String url = "jdbc:h2:file:" + directory.resolve("hprtas").toString();
    final Map<String, Object> payment =
        Vars.of("chargeAmount", 250, "fundingRoute", "patient", "paymentReference", "PAY-STORE-1");

    // One run of the worker, over a file store, and the worker process is then gone.
    final Database first = Database.open(new Config.DatabaseSettings(url, "sa", ""));
    final Services firstServices = TestSupport.buildServices(CONFIG, first);
    assertEquals(
        "completed",
        completed(PAYMENT_PROCESSING.handle(payment, TestSupport.buildContext(CONFIG, firstServices)))
            .get("paymentStatus"));
    first.close();

    // A new worker process, on the same file: the same request is a duplicate, not a second charge.
    final Database second = Database.open(new Config.DatabaseSettings(url, "sa", ""));
    final Services secondServices = TestSupport.buildServices(CONFIG, second);
    final Map<String, Object> retried =
        completed(PAYMENT_PROCESSING.handle(payment, TestSupport.buildContext(CONFIG, secondServices)));

    assertEquals("duplicate", retried.get("paymentStatus"));
    assertEquals(true, retried.get("duplicateAttempt"));
    assertEquals(250, retried.get("paidAmount"));
    second.close();
  }

  @Test
  @DisplayName("a patient the register already holds is found and their details are used (FR-048, NFR-004)")
  void aKnownPatientIsRetrieved() {
    final Services services = TestSupport.buildServices(CONFIG);
    seedPatient(services.database(), "PAT-1001", "9434765919", "Ada Lovelace", "digital");

    final WorkerContext context = TestSupport.buildContext(CONFIG, services);
    final Map<String, Object> variables =
        completed(
            REFERRAL_VALIDATION.handle(
                Vars.of(
                    "referralId", "REF-2026-0100",
                    "patientId", "PAT-1001",
                    "nhsNumber", "9434765919",
                    "patientName", "Ada Lovelace",
                    "documentsComplete", true,
                    "referringOrganisation", "St Mary GP Surgery"),
                context));

    assertEquals(true, variables.get("patientRecordFound"));
    assertEquals(true, variables.get("patientDetailsMatch"));
    assertEquals("Ada Lovelace", variables.get("patientName"));
    assertEquals("digital", variables.get("patientChannelPreference"));
    assertEquals(0, variables.get("patientPreviousReferrals"));

    // And the referral is recorded against them, so the next referral sees the history.
    final Map<String, Object> second =
        completed(
            REFERRAL_VALIDATION.handle(
                Vars.of(
                    "referralId", "REF-2026-0101",
                    "patientId", "PAT-1001",
                    "nhsNumber", "9434765919",
                    "patientName", "Ada Lovelace",
                    "documentsComplete", true,
                    "referringOrganisation", "Northside Hospital"),
                context));
    assertEquals(true, second.get("patientRecordFound"));
    assertEquals(1, second.get("patientPreviousReferrals"));
    assertEquals(
        Optional.of("Northside Hospital"),
        services.database().findReferral("REF-2026-0101").map(Database.Referral::referringOrganisation));
  }

  @Test
  @DisplayName("a name that disagrees with the register is flagged and the register is not overwritten (BR-46)")
  void aNameThatDisagreesIsFlagged() {
    final Services services = TestSupport.buildServices(CONFIG);
    seedPatient(services.database(), "PAT-1001", "9434765919", "Ada Lovelace", null);

    final Map<String, Object> variables =
        completed(
            REFERRAL_VALIDATION.handle(
                Vars.of(
                    "referralId", "REF-2026-0102",
                    "patientId", "PAT-1001",
                    "nhsNumber", "9434765919",
                    "patientName", "A. Lovelace",
                    "documentsComplete", true,
                    "referringOrganisation", "St Mary GP Surgery"),
                TestSupport.buildContext(CONFIG, services)));

    assertEquals(true, variables.get("patientRecordFound"));
    assertEquals(false, variables.get("patientDetailsMatch"));
    assertEquals("Ada Lovelace", variables.get("patientName"));
    assertEquals(
        Optional.of("Ada Lovelace"),
        services.database().findPatient("PAT-1001").map(Database.Patient::patientName));
  }

  @Test
  @DisplayName("one NHS number under two hospital identifiers is reported, not recorded twice (BR-46)")
  void oneNhsNumberIsOnePatient() {
    final Services services = TestSupport.buildServices(CONFIG);
    seedPatient(services.database(), "PAT-1001", "9434765919", "Ada Lovelace", null);

    final Map<String, Object> variables =
        completed(
            REFERRAL_VALIDATION.handle(
                Vars.of(
                    "referralId", "REF-2026-0106",
                    // the same patient, arrived under a different hospital identifier - which is
                    // what a second referral from another organisation looks like
                    "patientId", "PAT-9900",
                    "nhsNumber", "9434765919",
                    "patientName", "Ada Lovelace",
                    "documentsComplete", true,
                    "referringOrganisation", "Northside Hospital"),
                TestSupport.buildContext(CONFIG, services)));

    assertEquals(true, variables.get("patientIdentifierConflict"));
    assertEquals(
        "PAT-1001",
        variables.get("patientId"),
        "the record the hospital already holds is the one the referral is filed under");
    assertEquals(1, services.database().appointments().size() + countPatients(services));
    assertEquals(
        Optional.of("PAT-1001"),
        services.database().findReferral("REF-2026-0106").map(Database.Referral::patientId));
  }

  private static int countPatients(Services services) {
    return services.database().findPatient("PAT-9900").isPresent() ? 2 : 1;
  }

  @Test
  @DisplayName("a patient the register does not hold is registered (FR-048)")
  void anUnknownPatientIsRegistered() {
    final Services services = TestSupport.buildServices(CONFIG);

    final Map<String, Object> variables =
        completed(
            REFERRAL_VALIDATION.handle(
                Vars.of(
                    "referralId", "REF-2026-0103",
                    "patientId", "PAT-2002",
                    "nhsNumber", "9434765827",
                    "patientName", "Grace Hopper",
                    "documentsComplete", true,
                    "referringOrganisation", "St Mary GP Surgery"),
                TestSupport.buildContext(CONFIG, services)));

    assertEquals(false, variables.get("patientRecordFound"));
    final Optional<Database.Patient> registered = services.database().findPatientByNhsNumber("9434765827");
    assertTrue(registered.isPresent(), "the patient should have been added to the register");
    assertEquals("Grace Hopper", registered.get().patientName());
  }

  @Test
  @DisplayName("an NHS number that is not ten digits identifies no patient (BR-46)")
  void anNhsNumberThatIsNotTenDigitsIsRefused() {
    final Outcome outcome =
        REFERRAL_VALIDATION.handle(
            Vars.of(
                "referralId", "REF-2026-0104",
                "patientId", "PAT-2002",
                "nhsNumber", "94347659",
                "documentsComplete", true,
                "referringOrganisation", "St Mary GP Surgery"),
            TestSupport.buildContext(CONFIG));

    assertEquals("businessError", outcome.kind());
    assertEquals(
        ErrorCode.INVALID_VARIABLE.name(), ((Outcome.BusinessError) outcome).errorCode());
    assertEquals(
        "invalid_patient_identification",
        ((Outcome.BusinessError) outcome).variables().get("validationResult"));
  }

  @Test
  @DisplayName("a rebooking supersedes the appointment it replaces and stores the new one (FR-016, AC-10)")
  void aRebookingSupersedesWhatItReplaces() {
    final Services services = TestSupport.buildServices(CONFIG);
    final WorkerContext context = TestSupport.buildContext(CONFIG, services);

    final Map<String, Object> booking =
        Vars.of(
            "speciality", "oncology",
            "priority", "routine",
            "patientId", "PAT-1001",
            "referralId", "REF-2026-0100");

    final Map<String, Object> first =
        completed(APPOINTMENT_AVAILABILITY.handle(TestSupport.with(booking, "requestedWindow", 14), context));
    assertEquals(false, first.get("supersededAppointment"));

    final Map<String, Object> second =
        completed(APPOINTMENT_AVAILABILITY.handle(TestSupport.with(booking, "requestedWindow", 30), context));
    assertEquals(true, second.get("supersededAppointment"));

    final List<Database.Appointment> stored = services.database().appointments();
    assertEquals(2, stored.size(), "both appointments are kept, one of them recorded as superseded");
    final Database.Appointment superseded =
        stored.stream()
            .filter(appointment -> "superseded".equals(appointment.status()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("the appointment the rebooking replaced is not marked"));
    assertEquals((String) first.get("appointmentDate"), superseded.appointmentDate());
    assertTrue(
        stored.stream().anyMatch(appointment -> "booked".equals(appointment.status())),
        "the appointment the second request made is the live one");
  }

  @Test
  @DisplayName("the channel a letter names is recorded against the patient and used for the next one (NFR-011, FR-051)")
  void aRecordedPreferenceIsUsedByTheNextLetter() {
    final Services services = TestSupport.buildServices(CONFIG);
    seedPatient(services.database(), "PAT-1001", "9434765919", "Ada Lovelace", null);
    final WorkerContext context = TestSupport.buildContext(CONFIG, services);

    final Map<String, Object> letter =
        Vars.of(
            "recipients", "patient, GP",
            "documentType", "appointment_letter",
            "patientId", "PAT-1001");

    final Map<String, Object> named =
        completed(
            CORRESPONDENCE_DISPATCH.handle(
                TestSupport.with(letter, "channelPreference", "digital"), context));
    assertEquals("digital", named.get("dispatchChannel"));
    assertEquals("request", named.get("channelSource"));
    assertEquals(
        Optional.of("digital"),
        services.database().findPatient("PAT-1001").map(Database.Patient::channelPreference),
        "the preference the form named should have been recorded against the patient");

    final Map<String, Object> unnamed = completed(CORRESPONDENCE_DISPATCH.handle(letter, context));
    assertEquals("digital", unnamed.get("dispatchChannel"));
    assertEquals("patient-record", unnamed.get("channelSource"));
    assertEquals(2, services.database().dispatchCount());
  }

  @Test
  @DisplayName("the reply to a missing-information request updates the recorded request (FR-003, EX-01)")
  void theReplyUpdatesTheRecordedRequest() {
    final Services services = TestSupport.buildServices(CONFIG);
    final WorkerContext context = TestSupport.buildContext(CONFIG, services);

    final Map<String, Object> request =
        Vars.of(
            "referralId", "REF-2026-0105",
            "requestedFrom", "Northside Hospital",
            "requestedItems", "diagnostic report");

    completed(REFERRAL_DOCUMENT_REQUEST.handle(request, context));
    final Optional<Database.InformationRequest> requested =
        services.database().findInformationRequest("REF-2026-0105");
    assertTrue(requested.isPresent(), "the request should have been recorded");
    assertEquals("requested", requested.get().status());
    assertNull(requested.get().suppliedDate());

    final Map<String, Object> receipt =
        completed(DOCUMENT_RECEIPT.handle(Vars.of("referralId", "REF-2026-0105"), context));

    assertEquals("supplied", receipt.get("informationRequestStatus"));
    assertEquals("2026-09-16", receipt.get("documentsReceivedDate"));
    final Optional<Database.InformationRequest> supplied =
        services.database().findInformationRequest("REF-2026-0105");
    assertEquals("supplied", supplied.orElseThrow().status());
    assertNotNull(supplied.orElseThrow().suppliedDate());
    assertFalse(
        supplied.orElseThrow().suppliedDate().isEmpty(), "the date the documents arrived is recorded");
  }
}
