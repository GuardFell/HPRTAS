package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.db.Database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Simulated external correspondence service.
 *
 * Letters to patients, GPs and other providers go out through an external service (FR-008). This
 * records what was dispatched, to which recipients, on which channel and when, which is what the
 * case asks the system to retain (FR-037). The record is written to the hospital's store, so the
 * dispatch history survives the worker process.
 *
 * The channel is the one the request named, or - when it named none and the process says which
 * patient the letter is for - the preference the hospital's register holds for that patient. That is
 * the case's rule that patients receive communication in their preferred form (NFR-011, FR-051): the
 * preference is not only recorded on a form, it is read back and used when a letter is dispatched.
 *
 * Limitations are recorded in {@code workers/README.md}: there is no printing, no postage and no
 * delivery confirmation, and an accessible format or an authorised representative is recorded as the
 * channel rather than produced.
 */
public class CorrespondenceService extends SimulatedService {

  public static final String DISPATCHED = "dispatched";

  public static final List<String> OUTCOMES = List.of(DISPATCHED);

  /**
   * The channels accepted at a referral, plus the default used when neither the request nor the
   * patient's record states one (NFR-012, AS-12).
   */
  public static final List<String> CHANNELS =
      List.of("post", "digital", "authorised_representative", "accessible_format");

  public static final String DEFAULT_CHANNEL = "post";

  private final Config.CorrespondenceSettings config;
  private final Database database;

  public CorrespondenceService(Config.CorrespondenceSettings config, Database database) {
    super(config.latencyMs());
    this.config = config;
    this.database = database;
  }

  /** What the service recorded. */
  public record DispatchResult(
      String dispatchDate,
      String dispatchChannel,
      String dispatchReference,
      List<Object> dispatchedTo,
      String channelSource) {}

  /**
   * @param recipients who the letter goes to
   * @param channelPreference the channel the request named, or null
   * @param patientId the patient the letter is for, used to read their recorded preference, or null
   */
  public DispatchResult dispatch(
      List<Object> recipients,
      String channelPreference,
      String documentType,
      Instant now,
      String patientId) {

    awaitLatency();

    synchronized (database) {
      final Optional<Database.Patient> patient =
          patientId == null ? Optional.empty() : database.findPatient(patientId);
      final String recordedPreference =
          patient.map(Database.Patient::channelPreference).orElse(null);

      final String dispatchChannel =
          firstSet(channelPreference, recordedPreference, DEFAULT_CHANNEL);
      final String channelSource =
          channelPreference != null && !channelPreference.isEmpty()
              ? "request"
              : (recordedPreference != null && !recordedPreference.isEmpty()
                  ? "patient-record"
                  : "default");

      // A preference a form names for a patient the hospital knows is recorded against them, so the
      // next letter that names none reaches them the same way (NFR-011, FR-051).
      if (channelSource.equals("request") && patient.isPresent()) {
        final Database.Patient known = patient.get();
        database.savePatient(
            new Database.Patient(
                known.patientId(),
                known.nhsNumber(),
                known.patientName(),
                dispatchChannel,
                known.firstSeen(),
                Validate.toIsoDate(now),
                known.referrals()));
      }

      final String dispatchDate = Validate.toIsoDate(now);
      final int number = database.nextReferenceNumber("correspondence");
      final String dispatchReference = "COR-" + now.toEpochMilli() + "-" + number;

      database.saveDispatch(
          new Database.Dispatch(
              dispatchReference,
              recipients == null ? null : recipients.toString(),
              dispatchChannel,
              documentType,
              dispatchDate,
              patientId));

      return new DispatchResult(
          dispatchDate, dispatchChannel, dispatchReference, recipients, channelSource);
    }
  }
}
