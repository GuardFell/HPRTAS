/**
 * End-to-end smoke test for the external workers.
 *
 * Run with `npm run test:smoke` while the engine is running. It deploys the
 * fixture process, starts the real workers, runs two process instances and
 * checks the variables that came back:
 *
 *   1. the normal path, which must leave a completed payment, a confirmed
 *      treatment booking and a dispatched letter
 *   2. a booking without clinical authorisation, which must take the modelled
 *      BPMN error path rather than stall the instance on an incident
 *
 * Unlike `npm test` this proves the part the unit tests cannot: that the worker
 * registers against the gateway, receives a job of its type, and returns its
 * result so the process continues. The output is the evidence for the
 * definition-of-done item "External worker".
 */

const path = require('node:path')
const assert = require('node:assert/strict')

const { Camunda8 } = require('@camunda8/sdk')

const { loadConfig, connectionConfig } = require('../../src/config')
const { createLogger } = require('../../src/logger')
const { createServices } = require('../../src/services')
const { createTaskHandler, WORKER_MODULES } = require('../../src/index')

const FIXTURE = path.join(__dirname, '..', 'fixtures', 'worker-smoke-test.bpmn')
const PROCESS_ID = 'worker-smoke-test'
const REQUEST_TIMEOUT_MS = 30000

/** The variables a complete referral-to-treatment scenario needs. */
function normalScenario(instanceSuffix) {
  return {
    documentsComplete: true,
    referringOrganisation: 'St Mary GP Surgery',
    speciality: 'Oncology',
    priority: 'routine',
    requestedWindow: 14,
    proposedTreatment: 'FOLFOX cycle 1',
    treatmentStartDate: '2026-10-01',
    specialResources: ['pharmacy', 'day unit'],
    clinicalAuthorised: true,
    chargeAmount: 250,
    fundingRoute: 'patient',
    paymentReference: `PAY-SMOKE-${instanceSuffix}`,
    recipients: ['patient', 'GP'],
    documentType: 'new_patient_clinic_letter',
  }
}

/**
 * `onReady` is read from the worker options when the worker is constructed, so
 * the latch has to exist before `createWorker` is called.
 */
function createReadyLatch() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, ready: resolve }
}

function withTimeout(promise, timeoutMs, message) {
  let timer
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(message)), timeoutMs)
    }),
  ]).finally(() => clearTimeout(timer))
}

/**
 * The elements a process instance actually visited, read from the Orchestration
 * Cluster API. This is how the smoke test shows which path was taken: a
 * boundary error event that is never reached would leave an incident instead,
 * and the expected end event would be missing.
 *
 * The API reads from the exported records, so the answer lags behind the
 * instance. The search is repeated until every element that is expected to be
 * there has appeared.
 */
async function visitedElementIds(processInstanceKey, expected, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs
  let ids = []

  for (;;) {
    const response = await fetch(
      `${process.env.CAMUNDA_REST_BASE_URL || 'http://localhost:8080'}/v2/element-instances/search`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ filter: { processInstanceKey: String(processInstanceKey) } }),
      }
    )
    if (!response.ok) {
      throw new Error(`searching element instances failed with HTTP ${response.status}`)
    }
    const body = await response.json()
    ids = [...new Set((body.items || []).map((item) => item.elementId))]

    if (expected.every((elementId) => ids.includes(elementId))) return ids
    if (Date.now() > deadline) {
      throw new Error(
        `the element instances for ${processInstanceKey} never showed ${expected
          .filter((elementId) => !ids.includes(elementId))
          .join(', ')}; saw ${ids.join(', ')}`
      )
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
}

async function main() {
  const config = loadConfig()
  const log = createLogger({ level: 'warn' })
  const services = createServices(config)

  // The SDK logs its own connection lifecycle at info level, which buries the
  // output that is kept as evidence. It reads this from the environment as well
  // as from the constructor, so both are set.
  process.env.ZEEBE_CLIENT_LOG_LEVEL = 'ERROR'
  process.env.CAMUNDA_LOG_LEVEL = 'error'

  const camunda = new Camunda8({
    ...connectionConfig(config),
    CAMUNDA_LOG_LEVEL: 'error',
    ZEEBE_CLIENT_LOG_LEVEL: 'ERROR',
  })
  const zeebe = camunda.getZeebeGrpcApiClient()

  const deployment = await zeebe.deployResource({ processFilename: FIXTURE })
  const deployed = deployment.deployments.find((entry) => entry.process?.bpmnProcessId === PROCESS_ID)
  assert.ok(deployed, `the fixture did not deploy a process with the id ${PROCESS_ID}`)
  process.stdout.write(
    `Deployed ${PROCESS_ID} version ${deployed.process.version} ` +
      `(process definition key ${deployed.process.processDefinitionKey})\n`
  )

  const latches = WORKER_MODULES.map(() => createReadyLatch())
  const workers = WORKER_MODULES.map((workerModule, index) => {
    const settings = config.workers[workerModule.name]
    return zeebe.createWorker({
      taskType: settings.taskType,
      taskHandler: createTaskHandler(workerModule, config, log, services),
      maxJobsToActivate: settings.maxJobsToActivate,
      timeout: settings.timeoutMs,
      id: `${workerModule.name}-smoke`,
      onReady: () => latches[index].ready(),
    })
  })
  await withTimeout(
    Promise.all(latches.map((latch) => latch.promise)),
    20000,
    'the workers did not connect to the gateway within 20 seconds'
  )
  process.stdout.write(`Registered ${workers.length} workers and they are polling.\n\n`)

  // --- Scenario 1: the normal path -----------------------------------------
  const completed = await zeebe.createProcessInstanceWithResult({
    bpmnProcessId: PROCESS_ID,
    variables: normalScenario('001'),
    requestTimeout: REQUEST_TIMEOUT_MS,
  })
  const normal = completed.variables

  process.stdout.write('Scenario 1 - normal path\n')
  for (const [key, value] of Object.entries(normal).sort()) {
    process.stdout.write(`  ${key} = ${JSON.stringify(value)}\n`)
  }

  assert.equal(normal.validationResult, 'complete', 'the referral should have been accepted as complete')
  assert.equal(normal.slotAvailable, true, 'an appointment should have been offered')
  assert.ok(normal.appointmentDate, 'an appointment date should have been returned')
  assert.equal(normal.bookingStatus, 'confirmed', 'the treatment booking should be confirmed')
  assert.match(String(normal.appointmentReference), /^TRT-/, 'the treatment appointment should be referenced')
  assert.equal(normal.paymentStatus, 'completed', 'the payment should be recorded as completed')
  assert.equal(normal.paidAmount, 250, 'the amount returned by the provider should be recorded')
  assert.equal(normal.dispatchChannel, 'post', 'the letter should be dispatched by post')
  assert.ok(normal.dispatchReference, 'the dispatch should be referenced')

  const expectedNormalPath = [
    'StartEvent_Smoke',
    'Task_ValidateReferral',
    'Task_AppointmentAvailability',
    'Task_TreatmentAvailability',
    'Task_ProcessPayment',
    'Task_SendCorrespondence',
    'EndEvent_Completed',
  ]
  const normalPath = await visitedElementIds(completed.processInstanceKey, expectedNormalPath)
  process.stdout.write(`  path: ${normalPath.join(' -> ')}\n`)
  for (const elementId of expectedNormalPath) {
    assert.ok(normalPath.includes(elementId), `${elementId} should have been visited`)
  }
  assert.ok(
    !normalPath.includes('EndEvent_NotAuthorised'),
    'the error path should not have been taken'
  )
  process.stdout.write('Scenario 1 passed.\n\n')

  // --- Scenario 2: a booking without clinical authorisation ----------------
  // The worker throws a BPMN error. The model catches it with a boundary event,
  // so the instance must complete on that path rather than stall on an incident.
  const unauthorised = await zeebe.createProcessInstanceWithResult({
    bpmnProcessId: PROCESS_ID,
    variables: { ...normalScenario('002'), clinicalAuthorised: false },
    requestTimeout: REQUEST_TIMEOUT_MS,
  })
  const blocked = unauthorised.variables

  process.stdout.write('Scenario 2 - booking without clinical authorisation\n')

  assert.equal(
    blocked.appointmentReference,
    undefined,
    'no treatment appointment should exist for an unauthorised request'
  )
  assert.equal(
    blocked.paymentStatus,
    undefined,
    'the process should not have reached payment after the error path was taken'
  )

  const errorPath = await visitedElementIds(unauthorised.processInstanceKey, [
    'Task_TreatmentAvailability',
    'EndEvent_NotAuthorised',
  ])
  process.stdout.write(`  path: ${errorPath.join(' -> ')}\n`)
  assert.ok(
    errorPath.includes('EndEvent_NotAuthorised'),
    'the boundary error event should have been taken'
  )
  assert.ok(
    !errorPath.includes('Task_ProcessPayment'),
    'the process must not continue past a refused booking'
  )
  process.stdout.write(
    'Scenario 2 passed: the BPMN error was caught by the boundary event, no appointment and no payment.\n\n'
  )

  process.stdout.write('Smoke test passed: the workers obtain work and return results end to end.\n')

  for (const worker of workers) worker.stop?.()
  await zeebe.close()
}

if (require.main === module) {
  main().catch((error) => {
    process.stderr.write(`\nSmoke test FAILED: ${error.message}\n`)
    process.exitCode = 1
  })
}
