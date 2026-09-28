package uk.ac.uwe.hprtas.workers;

import uk.ac.uwe.hprtas.workers.services.Services;

import java.time.Instant;

/**
 * Everything a handler is given besides the process variables.
 *
 * `now` is passed in rather than read from the clock inside a handler, so the dates a handler writes
 * are the dates of the job it is serving and a test can fix them.
 *
 * `jobKey` is passed in for the same reason a handler that publishes a message needs an identity it
 * can make the publication idempotent with: retries of one job keep the key they were activated
 * with, so a publication derived from it is one publication however often the job is retried.
 */
public record WorkerContext(
    Instant now,
    long jobKey,
    long instanceKey,
    Services services,
    MessagePublisher messages,
    Config config,
    JsonLogger log) {}
