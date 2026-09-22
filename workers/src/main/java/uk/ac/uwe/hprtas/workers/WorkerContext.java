package uk.ac.uwe.hprtas.workers;

import uk.ac.uwe.hprtas.workers.services.Services;

import java.time.Instant;

/**
 * Everything a handler is given besides the process variables.
 *
 * `now` is passed in rather than read from the clock inside a handler, so the dates a handler writes
 * are the dates of the job it is serving and a test can fix them.
 */
public record WorkerContext(
    Instant now, long instanceKey, Services services, Config config, JsonLogger log) {}
