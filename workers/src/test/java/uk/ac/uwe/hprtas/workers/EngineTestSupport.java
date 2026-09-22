package uk.ac.uwe.hprtas.workers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the tests that drive the real engine.
 *
 * The engine tests are tagged {@code engine} and are not part of the default build, because they need
 * Camunda 8 Run to be up and they change what is deployed in it. Everything they read back comes from
 * the Orchestration Cluster API rather than from the client, so the path an instance took is read
 * from the engine's own record of it.
 */
final class EngineTestSupport {

  static final String BASE_URL =
      System.getenv("CAMUNDA_REST_BASE_URL") != null
          ? System.getenv("CAMUNDA_REST_BASE_URL")
          : "http://localhost:8080";

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private EngineTestSupport() {}

  /** Whether the engine is answering, so an engine test can be skipped rather than failed. */
  static boolean engineIsRunning() {
    try {
      final HttpResponse<String> response =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(BASE_URL + "/v2/topology"))
                  .timeout(Duration.ofSeconds(8))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception notRunning) {
      return false;
    }
  }

  /** Messages for the skip, so a skipped run says why rather than looking like a pass. */
  static String engineNotRunningMessage() {
    return "Camunda 8 Run is not answering at "
        + BASE_URL
        + "; start it with camunda-runtime\\start-camunda.bat and run this again";
  }

  /** POSTs JSON to the Orchestration Cluster API and returns the parsed body. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> post(String route, Map<String, Object> body) {
    try {
      final HttpResponse<String> response =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(BASE_URL + route))
                  .timeout(Duration.ofSeconds(30))
                  .header("content-type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      final String text = response.body();
      final Map<String, Object> parsed =
          text == null || text.isBlank()
              ? new LinkedHashMap<>()
              : Json.mapper().readValue(text, LinkedHashMap.class);

      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            route + " failed with HTTP " + response.statusCode() + ": " + parsed.get("detail"));
      }
      return parsed;
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(route + " failed: " + e.getMessage(), e);
    }
  }

  /** The elements a process instance visited, read back from the engine. */
  @SuppressWarnings("unchecked")
  static List<String> visitedElementIds(long processInstanceKey) {
    final Map<String, Object> body =
        post(
            "/v2/element-instances/search",
            Map.of("filter", Map.of("processInstanceKey", String.valueOf(processInstanceKey))));

    final List<String> ids = new ArrayList<>();
    for (Object item : (List<Object>) body.getOrDefault("items", List.of())) {
      final String elementId = String.valueOf(((Map<String, Object>) item).get("elementId"));
      if (!ids.contains(elementId)) {
        ids.add(elementId);
      }
    }
    return ids;
  }

  /**
   * The same, waiting until every element that is expected has appeared.
   *
   * The API reads from the exported records, so the answer lags behind the instance. Without the
   * wait an element that was visited a moment ago would look like one that never was.
   */
  static List<String> visitedElementIds(long processInstanceKey, List<String> expected, long timeoutMs) {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    List<String> ids = List.of();

    while (true) {
      ids = visitedElementIds(processInstanceKey);
      if (ids.containsAll(expected) || System.currentTimeMillis() > deadline) {
        return ids;
      }
      sleep(500);
    }
  }

  /** An open user task, as the Tasklist API reports it. */
  record UserTask(String userTaskKey, String elementId, String state) {}

  @SuppressWarnings("unchecked")
  static List<UserTask> openUserTasks(long processInstanceKey) {
    final Map<String, Object> body =
        post(
            "/v2/user-tasks/search",
            Map.of(
                "filter",
                Map.of("processInstanceKey", String.valueOf(processInstanceKey), "state", "CREATED")));

    final List<UserTask> tasks = new ArrayList<>();
    for (Object item : (List<Object>) body.getOrDefault("items", List.of())) {
      final Map<String, Object> task = (Map<String, Object>) item;
      tasks.add(
          new UserTask(
              String.valueOf(task.get("userTaskKey")),
              String.valueOf(task.get("elementId")),
              String.valueOf(task.get("state"))));
    }
    return tasks;
  }

  /** Completes a user task the way a Tasklist user completes it. */
  static void completeUserTask(String userTaskKey, Map<String, Object> variables) {
    try {
      final HttpResponse<String> response =
          HTTP.send(
              HttpRequest.newBuilder(
                      URI.create(BASE_URL + "/v2/user-tasks/" + userTaskKey + "/completion"))
                  .timeout(Duration.ofSeconds(30))
                  .header("content-type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          Json.write(Map.of("variables", variables))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "completing user task " + userTaskKey + " failed with HTTP " + response.statusCode());
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("completing user task " + userTaskKey + " failed", e);
    }
  }

  @SuppressWarnings("unchecked")
  static String instanceState(long processInstanceKey) {
    final Map<String, Object> body =
        post(
            "/v2/process-instances/search",
            Map.of("filter", Map.of("processInstanceKey", String.valueOf(processInstanceKey))));

    final List<Object> items = (List<Object>) body.getOrDefault("items", List.of());
    if (items.isEmpty()) {
      return null;
    }
    return String.valueOf(((Map<String, Object>) items.get(0)).get("state"));
  }

  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted", e);
    }
  }
}
