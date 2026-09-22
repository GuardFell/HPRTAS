package uk.ac.uwe.hprtas.workers;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the Camunda client from the resolved connection settings.
 *
 * The address carries its protocol, as it always has here: {@code grpc://} for a local, insecure
 * connection and {@code grpcs://} for a secured one. That is also what decides whether the connection
 * is in the clear, so there is no separate TLS setting to get out of step with it. The client itself
 * spells those two as {@code http://} and {@code https://}, so the scheme is translated on the way in
 * and the configuration keeps the vocabulary the models and the documentation already use.
 *
 * The client is told not to read the environment itself. Everything it needs has already been
 * resolved by {@link Config}, which is the only place that knows about {@code .env}, the shell and
 * the configuration files; a setting the client picked up on its own would bypass that.
 */
public final class CamundaClients {

  public static final String DEFAULT_ADDRESS = "grpc://localhost:26500";

  private CamundaClients() {}

  public static CamundaClient create(Config config) {
    final Map<String, Object> connection = config.connection();
    final String address =
        String.valueOf(connection.getOrDefault("ZEEBE_GRPC_ADDRESS", DEFAULT_ADDRESS));
    final String strategy =
        String.valueOf(connection.getOrDefault("CAMUNDA_AUTH_STRATEGY", "NONE"))
            .toUpperCase(Locale.ROOT);

    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .grpcAddress(grpcAddress(address))
            .applyEnvironmentVariableOverrides(false);

    switch (strategy) {
      case "NONE", "" -> {
        // Camunda 8 Run is installed unsecured for API access, so nothing is sent and no token is
        // fetched. The plaintext decision is the scheme's: http:// is in the clear.
      }

      case "BASIC" ->
          builder.credentialsProvider(
              CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                  .username(required(config, "CAMUNDA_BASIC_AUTH_USERNAME", strategy))
                  .password(valueOr(config.environment("CAMUNDA_BASIC_AUTH_PASSWORD"), ""))
                  .applyEnvironmentOverrides(false)
                  .build());

      case "OAUTH" -> {
        final String authorisationServerUrl = required(config, "CAMUNDA_OAUTH_URL", strategy);
        final String audience = valueOr(config.environment("CAMUNDA_TOKEN_AUDIENCE"), "");

        final OAuthCredentialsProviderBuilder oauth =
            CredentialsProvider.newCredentialsProviderBuilder()
                .clientId(required(config, "CAMUNDA_CLIENT_ID", strategy))
                .clientSecret(required(config, "CAMUNDA_CLIENT_SECRET", strategy))
                .authorizationServerUrl(authorisationServerUrl);
        if (!audience.isEmpty()) {
          oauth.audience(audience);
        }
        builder.credentialsProvider(oauth.build());
      }

      default ->
          throw new IllegalStateException(
              "CAMUNDA_AUTH_STRATEGY is \""
                  + strategy
                  + "\" but must be one of NONE, BASIC, OAUTH");
    }

    return builder.build();
  }

  /** The address in the scheme the client reads, which is where plaintext and TLS are decided. */
  static URI grpcAddress(String address) {
    final String hostAndPort =
        switch (schemeOf(address)) {
          case "grpc" -> "http://" + withoutScheme(address);
          case "grpcs" -> "https://" + withoutScheme(address);
          case "http", "https" -> address;
          default ->
              throw new IllegalStateException(
                  "the Zeebe gRPC address \""
                      + address
                      + "\" must include its protocol, for example grpc://localhost:26500");
        };
    return URI.create(hostAndPort);
  }

  private static String schemeOf(String address) {
    final int separator = address.indexOf("://");
    return separator < 0 ? "" : address.substring(0, separator).toLowerCase(Locale.ROOT);
  }

  private static String withoutScheme(String address) {
    final String hostAndPort = address.substring(address.indexOf("://") + 3);
    if (hostAndPort.isBlank()) {
      throw new IllegalStateException("the Zeebe gRPC address has no host: " + address);
    }
    return hostAndPort;
  }

  private static String required(Config config, String name, String strategy) {
    final String value = config.environment(name);
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException(
          "CAMUNDA_AUTH_STRATEGY is " + strategy + " but " + name + " is not set");
    }
    return value;
  }

  private static String valueOr(String value, String fallback) {
    return value != null ? value : fallback;
  }
}
