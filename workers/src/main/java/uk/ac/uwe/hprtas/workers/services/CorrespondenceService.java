package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulated external correspondence service.
 *
 * Letters to patients, GPs and other providers go out through an external service (FR-008). This
 * records what was dispatched, to which recipients, on which channel and when, which is what the
 * case asks the system to retain (FR-037).
 *
 * Limitations are recorded in {@code workers/README.md}: there is no printing, no postage and no
 * delivery confirmation, and the patient's channel preference is recorded and echoed but is not
 * honoured end to end.
 */
public class CorrespondenceService extends SimulatedService {

  public static final String DISPATCHED = "dispatched";

  public static final List<String> OUTCOMES = List.of(DISPATCHED);

  /**
   * The channels accepted at a referral, plus the default used when the patient has not stated a
   * preference (NFR-012, AS-12).
   */
  public static final List<String> CHANNELS =
      List.of("post", "digital", "authorised_representative", "accessible_format");

  public static final String DEFAULT_CHANNEL = "post";

  private final Config.CorrespondenceSettings config;
  private final List<Dispatch> dispatches = new java.util.ArrayList<>();

  public CorrespondenceService(Config.CorrespondenceSettings config) {
    super(config.latencyMs());
    this.config = config;
  }

  private record Dispatch(
      List<Object> recipients,
      String dispatchChannel,
      String documentType,
      String dispatchDate,
      String dispatchReference) {}

  /** What the service recorded. */
  public record DispatchResult(
      String dispatchDate,
      String dispatchChannel,
      String dispatchReference,
      List<Object> dispatchedTo) {}

  /**
   * @param recipients who the letter goes to
   * @param channelPreference the patient's recorded preference, or null
   */
  public DispatchResult dispatch(
      List<Object> recipients, String channelPreference, String documentType, Instant now) {

    awaitLatency();

    synchronized (dispatches) {
      final String dispatchChannel = firstSet(channelPreference, DEFAULT_CHANNEL);
      final String dispatchDate = Validate.toIsoDate(now);
      final String dispatchReference = "COR-" + now.toEpochMilli() + "-" + (dispatches.size() + 1);

      dispatches.add(
          new Dispatch(recipients, dispatchChannel, documentType, dispatchDate, dispatchReference));

      return new DispatchResult(dispatchDate, dispatchChannel, dispatchReference, recipients);
    }
  }
}
