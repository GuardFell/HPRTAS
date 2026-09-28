package uk.ac.uwe.hprtas.workers;

import io.camunda.client.CamundaClient;

import java.util.Map;

/** Publishes messages through the Zeebe gateway. */
final class ClientMessagePublisher implements MessagePublisher {

  private final CamundaClient client;

  ClientMessagePublisher(CamundaClient client) {
    this.client = client;
  }

  @Override
  public long publish(
      String messageName, String correlationKey, String messageId, Map<String, Object> variables) {

    return client
        .newPublishMessageCommand()
        .messageName(messageName)
        // A subscription is keyed on a value, so a publication that carries none cannot be what a
        // catch event is waiting for. Publishing without a key is a different command in this
        // client, which is how the two are kept from being confused: the message start events the
        // models declare hold no subscription, and nothing here publishes those.
        .correlationKey(correlationKey)
        .messageId(messageId)
        .variables(variables == null ? Map.of() : variables)
        .timeToLive(TIME_TO_LIVE)
        .send()
        .join()
        .getMessageKey();
  }
}
