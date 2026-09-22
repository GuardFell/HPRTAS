package uk.ac.uwe.hprtas.workers;

/**
 * Business error codes.
 *
 * These are the contract with the models: a model distinguishes the reason a step failed by which
 * error catch event was taken, so the code a worker throws has to match the {@code errorCode} of a
 * catch event on the service task it serves. A code with no catch event on the task becomes an
 * incident instead, which is still a recorded and visible outcome.
 */
public enum ErrorCode {
  /** A required variable is missing, is of the wrong type, or the values contradict each other. */
  INVALID_VARIABLE,

  /** Raised by referral-validation when a referral is incomplete but names no missing items. */
  MISSING_INFORMATION_NOT_SPECIFIED,

  /** Raised when card or security details are supplied. The provider is not called at all. */
  PROHIBITED_FINANCIAL_DATA,

  /** Raised by treatment-availability when a booking request has no clinical authorisation. */
  UNAUTHORISED_BOOKING_REQUEST
}
