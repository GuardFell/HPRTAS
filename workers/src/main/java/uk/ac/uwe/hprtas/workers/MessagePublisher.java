package uk.ac.uwe.hprtas.workers;

import java.time.Duration;
import java.util.Map;

/**
 * Publishes BPMN messages.
 *
 * A step that has to tell another participant something publishes a message rather than calling its
 * service, because the receiving participant is not the hospital's to call: it is a separate pool,
 * and the process waits on what it sends. The publication carries a correlation key, which is the
 * value the receiving side names in its subscription - that value, and not the message name alone,
 * is what selects the process instance that the message belongs to.
 *
 * It is an interface so a handler can be tested without an engine. The tests publish into a recorder
 * and assert on what was sent, and one of them makes the publication fail to prove the job is failed
 * and left for the broker to retry rather than completed.
 */
public interface MessagePublisher {

  /**
   * How long a publication is held when nothing is subscribed yet.
   *
   * The guide's teaching example uses ten minutes. A shorter life is used here on purpose: a message
   * that outlives its moment can still be correlated by a later instance waiting on the same key,
   * which would move a process the message was never meant for. Two minutes is long enough for the
   * receiving instance to reach its catch event and short enough that a stale publication expires.
   */
  Duration TIME_TO_LIVE = Duration.ofMinutes(2);

  /**
   * Publishes one message and returns the key the engine recorded it under.
   *
   * @param messageName the name the receiving subscription waits on
   * @param correlationKey the value that subscription is correlated by
   * @param messageId makes publishing the same message twice one publication, which is what stops a
   *     retried job sending a second copy; the caller derives it from the job, so a retry of the
   *     same job is idempotent while a genuinely new job publishes again
   * @param variables the payload
   */
  long publish(
      String messageName, String correlationKey, String messageId, Map<String, Object> variables);
}
