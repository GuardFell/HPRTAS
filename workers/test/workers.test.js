/**
 * Tests for the external workers.
 *
 * Each test states the scenario it belongs to in `tests/test-plan.md` where one
 * exists, so the evidence for a test case can point at a named test. Run with
 * `npm test`. No engine is required.
 */

const test = require('node:test')
const assert = require('node:assert/strict')

const { buildConfig, buildContext, buildJob, silentLogger } = require('./helpers')
const { createTaskHandler } = require('../src/index')

const referralValidation = require('../src/workers/referral-validation')
const appointmentAvailability = require('../src/workers/appointment-availability')
const treatmentAvailability = require('../src/workers/treatment-availability')
const paymentProcessing = require('../src/workers/payment-processing')
const correspondenceDispatch = require('../src/workers/correspondence-dispatch')

const config = buildConfig()

/** Runs a handler and returns the outcome, failing the test if it throws. */
async function run(workerModule, variables, overrides = {}) {
  const context = buildContext(config, overrides)
  return workerModule.handle(variables, context)
}

// ---------------------------------------------------------------------------
// referral-validation
// ---------------------------------------------------------------------------

test('referral-validation: a complete referral is accepted for clinical review (TC-01)', async () => {
  const outcome = await run(referralValidation, {
    documentsComplete: true,
    referringOrganisation: 'St Mary GP Surgery',
  })

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.validationResult, 'complete')
  assert.deepEqual(outcome.variables.requestedItems, [])
})

test('referral-validation: missing supporting information is requested from the referring organisation (TC-03)', async () => {
  const outcome = await run(referralValidation, {
    documentsComplete: false,
    missingItems: 'previous clinic letter, diagnostic report',
    referringOrganisation: 'Northside Hospital',
  })

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.validationResult, 'incomplete')
  assert.deepEqual(outcome.variables.requestedItems, [
    'previous clinic letter',
    'diagnostic report',
  ])
  assert.equal(outcome.variables.missingInformationRequestedFrom, 'Northside Hospital')
})

test('referral-validation: an incomplete referral that names no missing items is a business error', async () => {
  const outcome = await run(referralValidation, {
    documentsComplete: false,
    referringOrganisation: 'Northside Hospital',
  })

  assert.equal(outcome.kind, 'businessError')
  assert.equal(outcome.errorCode, 'MISSING_INFORMATION_NOT_SPECIFIED')
})

test('referral-validation: unusable input produces a controlled error, not a crash (TC-11)', async () => {
  const missing = await run(referralValidation, {})
  assert.equal(missing.kind, 'businessError')
  assert.equal(missing.errorCode, 'INVALID_VARIABLE')

  const contradictory = await run(referralValidation, {
    documentsComplete: true,
    missingItems: ['diagnostic report'],
    referringOrganisation: 'Northside Hospital',
  })
  assert.equal(contradictory.kind, 'businessError')
  assert.equal(contradictory.errorCode, 'INVALID_VARIABLE')

  const blankOrganisation = await run(referralValidation, {
    documentsComplete: true,
    referringOrganisation: '   ',
  })
  assert.equal(blankOrganisation.kind, 'businessError')
  assert.equal(blankOrganisation.errorCode, 'INVALID_VARIABLE')
})

test('referral-validation: the check is administrative and returns no clinical judgement (BR-01)', async () => {
  const outcome = await run(referralValidation, {
    documentsComplete: true,
    referringOrganisation: 'St Mary GP Surgery',
  })

  const clinicalWords = ['clinical', 'suitab', 'accept', 'reject', 'diagnos', 'triage']
  const written = Object.keys(outcome.variables).map((key) => key.toLowerCase())
  for (const word of clinicalWords) {
    assert.ok(
      !written.some((key) => key.includes(word)),
      `the worker must not write a variable that looks like a clinical decision, found "${word}"`
    )
  }
})

// ---------------------------------------------------------------------------
// appointment-availability
// ---------------------------------------------------------------------------

test('appointment-availability: a slot inside the requested window is offered (TC-01)', async () => {
  const outcome = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'routine',
    requestedWindow: 14,
  })

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.slotAvailable, true)
  assert.equal(outcome.variables.appointmentDate, '2026-09-26')
  assert.equal(outcome.variables.withinRequestedWindow, true)
})

test('appointment-availability: an appointment inside two weeks is flagged for telephone contact (TC-04, BR-03)', async () => {
  const outcome = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'urgent',
    requestedWindow: 14,
  })

  assert.equal(outcome.variables.appointmentWithinTwoWeeks, true)
})

test('appointment-availability: no slot inside the requested period is offered as an alternative and not booked (TC-05, BR-17)', async () => {
  const outcome = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'routine',
    requestedWindow: 14,
    schedulingOutcome: 'none',
  })

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.slotAvailable, false)
  assert.equal(outcome.variables.appointmentDate, null)
  assert.equal(outcome.variables.alternativeDate, '2026-10-21')
  assert.equal(outcome.variables.withinRequestedWindow, false)
})

test('appointment-availability: a slot outside the requested period is not silently accepted (TC-18, EX-06)', async () => {
  const outcome = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'routine',
    requestedWindow: 14,
    schedulingOutcome: 'outside_window',
  })

  assert.equal(outcome.variables.slotAvailable, false)
  assert.equal(outcome.variables.schedulingServiceOutcome, 'outside_window')
})

test('appointment-availability: asking twice for the same instance returns the same appointment', async () => {
  const variables = { speciality: 'Oncology', priority: 'routine', requestedWindow: 14 }
  const context = buildContext(config)

  const first = await appointmentAvailability.handle(variables, context)
  const second = await appointmentAvailability.handle(variables, context)

  assert.equal(second.variables.appointmentDate, first.variables.appointmentDate)
})

test('appointment-availability: an unusable booking request produces a controlled error (TC-11)', async () => {
  const badWindow = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'routine',
    requestedWindow: 0,
  })
  assert.equal(badWindow.kind, 'businessError')
  assert.equal(badWindow.errorCode, 'INVALID_VARIABLE')

  const badPriority = await run(appointmentAvailability, {
    speciality: 'Oncology',
    priority: 'soon',
    requestedWindow: 14,
  })
  assert.equal(badPriority.kind, 'businessError')
  assert.equal(badPriority.errorCode, 'INVALID_VARIABLE')
})

// ---------------------------------------------------------------------------
// treatment-availability
// ---------------------------------------------------------------------------

const authorisedRequest = {
  proposedTreatment: 'FOLFOX cycle 1',
  treatmentStartDate: '2026-10-01',
  specialResources: 'pharmacy, day unit',
  clinicalAuthorised: true,
}

test('treatment-availability: an authorised request is booked (TC-07)', async () => {
  const outcome = await run(treatmentAvailability, authorisedRequest)

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.bookingStatus, 'confirmed')
  assert.match(outcome.variables.appointmentReference, /^TRT-/)
  assert.equal(outcome.variables.externalServiceAvailable, true)
})

test('treatment-availability: a request without clinical authorisation is not processed (TC-06, BR-04, AC-02)', async () => {
  const outcome = await run(treatmentAvailability, {
    ...authorisedRequest,
    clinicalAuthorised: false,
  })

  assert.equal(outcome.kind, 'businessError')
  assert.equal(outcome.errorCode, 'UNAUTHORISED_BOOKING_REQUEST')
  assert.equal(outcome.variables.bookingStatus, 'not_processed')
})

test('treatment-availability: an unavailable service leaves the booking pending and records the attempt (TC-12, EX-07)', async () => {
  const context = buildContext(config)
  const variables = { ...authorisedRequest, treatmentOutcome: 'unavailable' }

  const first = await treatmentAvailability.handle(variables, context)
  const second = await treatmentAvailability.handle(variables, context)

  assert.equal(first.kind, 'completed')
  assert.equal(first.variables.externalServiceAvailable, false)
  assert.equal(first.variables.bookingStatus, 'pending')
  assert.equal(first.variables.appointmentReference, null)
  assert.equal(first.variables.treatmentRetryCount, 1)
  assert.equal(second.variables.treatmentRetryCount, 2, 'each further attempt is recorded')
})

test('treatment-availability: retrying after an outage creates no second appointment (AC-10)', async () => {
  const context = buildContext(config)

  await treatmentAvailability.handle(
    { ...authorisedRequest, treatmentOutcome: 'unavailable' },
    context
  )
  const booked = await treatmentAvailability.handle(
    { ...authorisedRequest, treatmentOutcome: 'available' },
    context
  )
  const retried = await treatmentAvailability.handle(
    { ...authorisedRequest, treatmentOutcome: 'available' },
    context
  )

  assert.equal(retried.variables.appointmentReference, booked.variables.appointmentReference)
  assert.equal(retried.variables.duplicateAttempt, true)
})

// ---------------------------------------------------------------------------
// payment-processing
// ---------------------------------------------------------------------------

const patientPayment = {
  chargeAmount: 250,
  fundingRoute: 'patient',
  paymentReference: 'PAY-2026-0001',
}

test('payment-processing: a completed payment returns the reference, date and amount (TC-07, FR-020)', async () => {
  const outcome = await run(paymentProcessing, patientPayment)

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.paymentStatus, 'completed')
  assert.equal(outcome.variables.paidAmount, 250)
  assert.equal(outcome.variables.confirmationReceived, true)
  assert.equal(outcome.variables.requiresInvestigation, false)
  assert.equal(outcome.variables.paymentDate, '2026-09-16')
})

test('payment-processing: a declined payment is reported so the patient and team can be notified (TC-08, EX-08)', async () => {
  const outcome = await run(paymentProcessing, { ...patientPayment, paymentOutcome: 'declined' })

  assert.equal(outcome.variables.paymentStatus, 'declined')
  assert.equal(outcome.variables.paidAmount, null)
  assert.equal(outcome.variables.requiresInvestigation, false)
})

test('payment-processing: a payment taken without a confirmation is marked for investigation (TC-09, BR-07, AC-04)', async () => {
  const outcome = await run(paymentProcessing, {
    ...patientPayment,
    paymentOutcome: 'success_no_confirmation',
  })

  assert.equal(outcome.variables.paymentStatus, 'completed')
  assert.equal(outcome.variables.confirmationReceived, false)
  assert.equal(outcome.variables.requiresInvestigation, true)
})

test('payment-processing: the same reference is never charged twice (FR-023, AC-10)', async () => {
  const context = buildContext(config)

  const first = await paymentProcessing.handle(patientPayment, context)
  const second = await paymentProcessing.handle(patientPayment, context)

  assert.equal(second.variables.paymentStatus, 'duplicate')
  assert.equal(second.variables.duplicateAttempt, true)
  assert.equal(
    second.variables.transactionReference,
    first.variables.transactionReference,
    'the original transaction is returned rather than a new one'
  )
  assert.equal(second.variables.paidAmount, first.variables.paidAmount)
})

test('payment-processing: a declined attempt may be retried without a double charge (EX-08)', async () => {
  const context = buildContext(config)

  const declined = await paymentProcessing.handle(
    { ...patientPayment, paymentOutcome: 'declined' },
    context
  )
  const retried = await paymentProcessing.handle(
    { ...patientPayment, paymentOutcome: 'completed' },
    context
  )

  assert.equal(declined.variables.paymentStatus, 'declined')
  assert.equal(retried.variables.paymentStatus, 'completed')
  assert.notEqual(retried.variables.transactionReference, declined.variables.transactionReference)
})

test('payment-processing: card details are refused and never passed on (BR-06, NFR-007, AC-09)', async () => {
  let providerCalled = false
  const context = buildContext(config)
  const originalRequestPayment = context.services.payment.requestPayment
  context.services.payment.requestPayment = (...args) => {
    providerCalled = true
    return originalRequestPayment(...args)
  }

  const outcome = await paymentProcessing.handle(
    { ...patientPayment, cardNumber: '4111111111111111', cvv: '123' },
    context
  )

  assert.equal(outcome.kind, 'businessError')
  assert.equal(outcome.errorCode, 'PROHIBITED_FINANCIAL_DATA')
  assert.equal(providerCalled, false, 'the provider must not be called with card details')
  assert.deepEqual(outcome.variables.prohibitedFields.sort(), ['cardNumber', 'cvv'])
})

test('payment-processing: a funded patient is not asked to pay', async () => {
  let providerCalled = false
  const context = buildContext(config)
  const originalRequestPayment = context.services.payment.requestPayment
  context.services.payment.requestPayment = (...args) => {
    providerCalled = true
    return originalRequestPayment(...args)
  }

  for (const fundingRoute of ['hospital', 'insurer', 'exempt']) {
    const outcome = await paymentProcessing.handle(
      { ...patientPayment, fundingRoute },
      context
    )
    assert.equal(outcome.variables.paymentStatus, 'not_required')
    assert.equal(outcome.variables.transactionReference, null)
  }
  assert.equal(providerCalled, false, 'the provider must not be called when no payment is due')
})

test('payment-processing: an unusable payment request produces a controlled error (TC-11)', async () => {
  for (const variables of [
    { ...patientPayment, chargeAmount: 'two hundred and fifty' },
    { ...patientPayment, chargeAmount: -5 },
    { ...patientPayment, fundingRoute: 'charity' },
    { ...patientPayment, paymentReference: '' },
    { chargeAmount: 250, fundingRoute: 'patient' },
  ]) {
    const outcome = await run(paymentProcessing, variables)
    assert.equal(outcome.kind, 'businessError', `expected a business error for ${JSON.stringify(variables)}`)
    assert.equal(outcome.errorCode, 'INVALID_VARIABLE')
  }
})

// ---------------------------------------------------------------------------
// correspondence-dispatch
// ---------------------------------------------------------------------------

test('correspondence-dispatch: a letter is dispatched and the date, channel and reference are recorded (FR-037)', async () => {
  const outcome = await run(correspondenceDispatch, {
    recipients: 'patient, GP',
    documentType: 'new_patient_clinic_letter',
  })

  assert.equal(outcome.kind, 'completed')
  assert.equal(outcome.variables.dispatchDate, '2026-09-16')
  assert.equal(outcome.variables.dispatchChannel, 'post', 'no preference defaults to post (AS-12)')
  assert.deepEqual(outcome.variables.recipients, ['patient', 'GP'])
})

test('correspondence-dispatch: a recorded channel preference is used (NFR-012)', async () => {
  const outcome = await run(correspondenceDispatch, {
    recipients: 'patient',
    documentType: 'appointment_letter',
    channelPreference: 'accessible_format',
  })

  assert.equal(outcome.variables.dispatchChannel, 'accessible_format')
})

test('correspondence-dispatch: an unusable dispatch request produces a controlled error (TC-11)', async () => {
  for (const variables of [
    { recipients: '', documentType: 'appointment_letter' },
    { recipients: 'patient', documentType: 'a letter of some kind' },
    { recipients: 'patient', documentType: 'appointment_letter', channelPreference: 'sms' },
  ]) {
    const outcome = await run(correspondenceDispatch, variables)
    assert.equal(outcome.kind, 'businessError', `expected a business error for ${JSON.stringify(variables)}`)
    assert.equal(outcome.errorCode, 'INVALID_VARIABLE')
  }
})

// ---------------------------------------------------------------------------
// The job contract itself
// ---------------------------------------------------------------------------

test('the worker turns a completed outcome into a job completion', async () => {
  const job = buildJob({
    documentsComplete: true,
    referringOrganisation: 'St Mary GP Surgery',
  })
  const handler = createTaskHandler(referralValidation, config, silentLogger(), buildContext(config).services)

  await handler(job)

  assert.equal(job.action.kind, 'complete')
  assert.equal(job.action.variables.validationResult, 'complete')
})

test('the worker turns a broken business rule into a BPMN error the model can catch', async () => {
  const job = buildJob({
    proposedTreatment: 'FOLFOX cycle 1',
    treatmentStartDate: '2026-10-01',
    clinicalAuthorised: false,
  })
  const handler = createTaskHandler(
    treatmentAvailability,
    config,
    silentLogger(),
    buildContext(config).services
  )

  await handler(job)

  assert.equal(job.action.kind, 'error')
  assert.equal(job.action.errorCode, 'UNAUTHORISED_BOOKING_REQUEST')
  assert.equal(job.action.variables.bookingStatus, 'not_processed')
})

test('the worker fails the job when the handler throws, so the broker retries', async () => {
  const brokenWorker = {
    name: 'referral-validation',
    taskType: 'validate-referral',
    handle: async () => {
      throw new Error('the simulated service is unreachable')
    },
  }
  const job = buildJob({})
  const handler = createTaskHandler(brokenWorker, config, silentLogger(), buildContext(config).services)

  await handler(job)

  assert.equal(job.action.kind, 'fail')
  assert.match(job.action.errorMessage, /simulated service is unreachable/)
})

test('the worker fails the job when a handler returns something that is not an outcome', async () => {
  const brokenWorker = {
    name: 'referral-validation',
    taskType: 'validate-referral',
    handle: async () => ({ validationResult: 'complete' }),
  }
  const job = buildJob({})
  const handler = createTaskHandler(brokenWorker, config, silentLogger(), buildContext(config).services)

  await handler(job)

  assert.equal(job.action.kind, 'fail')
})
