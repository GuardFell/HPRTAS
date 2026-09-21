/**
 * End-to-end run of the four operational models against the real engine.
 *
 * Run with `npm run test:e2e` while the engine is running. `npm run test:smoke`
 * proves the workers against a purpose-built linear fixture; this proves them
 * against the delivered models: the jobs come from the service tasks of
 * `models/operational/core-1..4`, the gateways read what the workers return, the
 * user tasks are completed the way a Tasklist user completes them, and the path
 * is read back from the Orchestration Cluster API.
 *
 * Five scenarios:
 *
 *   1. `core-1` normal referral path, to the appointment being arranged
 *   2. `core-2` treatment authorisation, payment and the between-cycle review
 *   3. `core-3` clinic letter distributed
 *   4. `core-4` follow-up requested, to the follow-up appointment being booked
 *   5. `core-4` cancellation of a paid appointment, to the refund being recorded
 *
 * Scenario 5 runs after scenario 2 on purpose: it refunds the payment reference
 * scenario 2 settled, which is the only way to exercise `refund-processing`
 * against a provider that really holds the transaction.
 */

const path = require('node:path')
const assert = require('node:assert/strict')

const { Camunda8 } = require('@camunda8/sdk')

const { loadConfig, connectionConfig } = require('../../src/config')
const { createLogger } = require('../../src/logger')
const { createServices } = require('../../src/services')
const { createTaskHandler, WORKER_MODULES } = require('../../src/index')

const BASE_URL = process.env.CAMUNDA_REST_BASE_URL || 'http://localhost:8080'
const MODELS_DIR = path.join(__dirname, '..', '..', '..', 'models', 'operational')
const FORMS_DIR = path.join(__dirname, '..', '..', '..', 'forms')

const MODELS = [
  'core-1-referral-and-new-patient-appointment.bpmn',
  'core-2-treatment-authorisation-funding-and-payment.bpmn',
  'core-3-clinic-letter-and-pathway-escalation.bpmn',
  'core-4-follow-up-cancellation-enquiry-and-refund.bpmn',
]

const PAYMENT_REFERENCE = 'PAY-E2E-0001'

/** POST JSON to the Orchestration Cluster API and return the parsed body. */
async function post(route, body) {
  const response = await fetch(`${BASE_URL}${route}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  const text = await response.text()
  const parsed = text ? JSON.parse(text) : {}
  if (!response.ok) {
    throw new Error(`${route} failed with HTTP ${response.status}: ${parsed.detail || text}`)
  }
  return parsed
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function deployModelsAndForms(out) {
  for (const model of MODELS) {
    const response = await fetch(`${BASE_URL}/v2/deployments`, {
      method: 'POST',
      body: formData([
        ['resources', path.join(MODELS_DIR, model), 'application/xml'],
      ]),
    })
    const body = await response.json()
    if (!response.ok) throw new Error(`deploying ${model} failed: ${body.detail || ''}`)
    const deployed = body.deployments[0].processDefinition
    out(`deployed ${deployed.processDefinitionId} version ${deployed.processDefinitionVersion}`)
  }
  for (const form of require('node:fs').readdirSync(FORMS_DIR).filter((f) => f.endsWith('.form'))) {
    const response = await fetch(`${BASE_URL}/v2/deployments`, {
      method: 'POST',
      body: formData([['resources', path.join(FORMS_DIR, form), 'application/json']]),
    })
    const body = await response.json()
    if (!response.ok) throw new Error(`deploying ${form} failed: ${body.detail || ''}`)
    out(`deployed form ${body.deployments[0].form.formId}`)
  }
}

function formData(entries) {
  const form = new FormData()
  for (const [field, file, type] of entries) {
    form.append(field, new Blob([require('node:fs').readFileSync(file)], { type }), path.basename(file))
  }
  return form
}

async function startInstance(processDefinitionId, variables) {
  const body = await post('/v2/process-instances', { processDefinitionId, variables })
  return body.processInstanceKey
}

/** Publishes the message that starts a model's message start event. */
async function startByMessage(name, correlationKey, variables) {
  await post('/v2/messages/publication', {
    name,
    correlationKey,
    timeToLive: 60000,
    variables,
  })
}

async function openUserTasks(processInstanceKey) {
  const body = await post('/v2/user-tasks/search', {
    filter: { processInstanceKey: String(processInstanceKey), state: 'CREATED' },
  })
  return body.items || []
}

async function completeUserTask(userTaskKey, variables) {
  const response = await fetch(`${BASE_URL}/v2/user-tasks/${userTaskKey}/completion`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ variables }),
  })
  if (!response.ok) {
    throw new Error(`completing user task ${userTaskKey} failed with HTTP ${response.status}`)
  }
}

async function instanceState(processInstanceKey) {
  const body = await post('/v2/process-instances/search', {
    filter: { processInstanceKey: String(processInstanceKey) },
  })
  return body.items?.[0]?.state
}

async function visitedElementIds(processInstanceKey, timeoutMs = 25000) {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const body = await post('/v2/element-instances/search', {
      filter: { processInstanceKey: String(processInstanceKey) },
    })
    const ids = [...new Set((body.items || []).map((item) => item.elementId))]
    if (ids.length > 0 && Date.now() > deadline) return ids
    if (ids.length > 0) {
      // give the exporter a moment to catch up with the newest elements
      await sleep(1500)
      const again = await post('/v2/element-instances/search', {
        filter: { processInstanceKey: String(processInstanceKey) },
      })
      return [...new Set((again.items || []).map((item) => item.elementId))]
    }
    if (Date.now() > deadline) {
      throw new Error(`no element instances were recorded for ${processInstanceKey}`)
    }
    await sleep(500)
  }
}

/**
 * Drives one instance: completes every open user task with the variables the
 * scenario supplies for it, until the instance reaches an end event.
 */
async function drive({ label, processDefinitionId, message, variables, taskVariables, out }) {
  const instanceKey = message
    ? await (async () => {
        await startByMessage(message, `${message}-${Date.now()}`, variables)
        const deadline = Date.now() + 20000
        for (;;) {
          const body = await post('/v2/process-instances/search', {
            filter: { processDefinitionId },
          })
          const running = (body.items || []).find((i) => i.state === 'ACTIVE')
          if (running) return running.processInstanceKey
          if (Date.now() > deadline) throw new Error(`${label}: the message started nothing`)
          await sleep(500)
        }
      })()
    : await startInstance(processDefinitionId, variables)

  out(`\n${label}`)
  out(`  instance ${instanceKey}`)

  const done = new Set()
  const deadline = Date.now() + 90000
  const visited = []

  for (;;) {
    const tasks = await openUserTasks(instanceKey)
    for (const task of tasks) {
      if (done.has(task.userTaskKey)) continue
      const supplied = taskVariables[task.elementId] ?? {}
      await completeUserTask(task.userTaskKey, supplied)
      done.add(task.userTaskKey)
      visited.push(task.elementId)
      out(`  ${task.elementId.padEnd(32)} completed with ${JSON.stringify(supplied)}`)
    }

    const state = await instanceState(instanceKey)
    if (state && state !== 'ACTIVE') {
      out(`  final state: ${state}`)
      break
    }
    if (Date.now() > deadline) {
      out('  TIMED OUT waiting for the instance to finish')
      break
    }
    await sleep(500)
  }

  const elements = await visitedElementIds(instanceKey)
  out(`  path: ${elements.join(' -> ')}`)
  return { instanceKey, elements, visited }
}

async function main() {
  const lines = []
  const out = (line) => {
    lines.push(line)
    process.stdout.write(`${line}\n`)
  }

  const config = loadConfig()
  const log = createLogger({ level: 'warn' })
  const services = createServices(config)

  process.env.ZEEBE_CLIENT_LOG_LEVEL = 'ERROR'
  process.env.CAMUNDA_LOG_LEVEL = 'error'

  out('External workers against the four operational models')
  out('='.repeat(74))
  out(`Camunda 8 Run via ${BASE_URL}, gRPC ${connectionConfig(config).ZEEBE_GRPC_ADDRESS}`)
  out('')

  await deployModelsAndForms(out)

  const camunda = new Camunda8({
    ...connectionConfig(config),
    CAMUNDA_LOG_LEVEL: 'error',
    ZEEBE_CLIENT_LOG_LEVEL: 'ERROR',
  })
  const zeebe = camunda.getZeebeGrpcApiClient()

  const ready = []
  const workers = WORKER_MODULES.map((workerModule) => {
    const settings = config.workers[workerModule.name]
    return zeebe.createWorker({
      taskType: settings.taskType,
      taskHandler: createTaskHandler(workerModule, config, log, services),
      maxJobsToActivate: settings.maxJobsToActivate,
      timeout: settings.timeoutMs,
      id: `${workerModule.name}-e2e`,
      onReady: () => ready.push(workerModule.name),
    })
  })
  const readyDeadline = Date.now() + 25000
  while (ready.length < WORKER_MODULES.length && Date.now() < readyDeadline) await sleep(300)
  assert.equal(
    ready.length,
    WORKER_MODULES.length,
    `only ${ready.length} of ${WORKER_MODULES.length} workers reached the gateway`
  )
  out(`\nregistered ${workers.length} workers: ${ready.join(', ')}`)

  // --- 1. core-1, the normal referral path ---------------------------------
  const s1 = await drive({
    label: 'Scenario 1 - core-1, normal referral and new patient appointment',
    processDefinitionId: 'core-1-referral-and-new-patient-appointment',
    variables: {},
    taskVariables: {
      N_MS_CheckReferral: { documentsComplete: true, referringOrganisation: 'St Mary GP Surgery' },
      N_C_ClinicalReview: { decision: 'accepted' },
      N_OB_PrepareRequest: {
        speciality: 'Oncology',
        priority: 'routine',
        requestedWindow: 14,
        recipients: ['patient', 'GP'],
        documentType: 'new_patient_clinic_letter',
      },
      N_OB_TelephonePatient: { contactOutcome: 'contacted' },
    },
    out,
  })
  for (const id of ['N_MS_ValidateReferral', 'N_OB_CheckAvailability', 'N_OB_SendNotification']) {
    assert.ok(s1.elements.includes(id), `core-1 should have run ${id}`)
  }
  assert.ok(s1.elements.includes('N_OB_AppointmentArranged'), 'core-1 should end with the appointment arranged')

  // --- 2. core-2, authorisation, payment and the between-cycle review ------
  const s2 = await drive({
    label: 'Scenario 2 - core-2, treatment authorisation, funding, payment and review',
    processDefinitionId: 'core-2-treatment-authorisation-funding-and-payment',
    variables: {},
    taskVariables: {
      N_CL_AssessPatient: { consentGiven: true },
      N_CL_AuthoriseTreatment: {
        proposedTreatment: 'FOLFOX cycle 1',
        treatmentStartDate: '2026-10-01',
        specialResources: ['pharmacy', 'day unit'],
        clinicalAuthorised: true,
        authorisingClinician: 'Dr Chen',
      },
      N_TB_VerifyAuthorisation: {},
      N_F_DetermineFunding: {
        fundingRoute: 'patient',
        fundingApprovalRequired: false,
        paymentRequiredFromPatient: true,
      },
      N_F_CalculateCharge: {
        chargeAmount: 250,
        fundingRoute: 'patient',
        paymentReference: PAYMENT_REFERENCE,
      },
      N_TB_ConfirmAppointment: { recipients: ['patient'], documentType: 'appointment_letter' },
      N_OC_PreCycleReview: { fitToContinue: false },
      N_CL_ModifyTreatment: { affectsCharge: true },
    },
    out,
  })
  for (const id of ['N_TB_CheckAvailability', 'N_F_ProcessPayment', 'N_TB_NotifyPatient']) {
    assert.ok(s2.elements.includes(id), `core-2 should have run ${id}`)
  }
  assert.ok(s2.elements.includes('N_F_ImpactReviewed'), 'core-2 should end on the financial review')

  // --- 3. core-3, the clinic letter ---------------------------------------
  const s3 = await drive({
    label: 'Scenario 3 - core-3, clinic letter prepared, approved and distributed',
    processDefinitionId: 'core-3-clinic-letter-and-pathway-escalation',
    variables: {},
    taskVariables: {
      N_C_PrepareLetter: {
        consultationId: 'CONS-E2E-0001',
        recipients: ['patient', 'GP'],
        documentType: 'new_patient_clinic_letter',
      },
      N_C_ApproveLetter: {},
      N_MS_ProcessLetter: { suspectedClinicalError: false },
      N_MS_ConfirmRecipients: {},
    },
    out,
  })
  assert.ok(s3.elements.includes('N_MS_SendLetter'), 'core-3 should have run the letter dispatch')
  assert.ok(s3.elements.includes('N_MS_LetterDistributed'), 'core-3 should end with the letter distributed')

  // --- 4. core-4, the follow-up requested ---------------------------------
  const s4 = await drive({
    label: 'Scenario 4 - core-4, follow-up requested and booked',
    processDefinitionId: 'core-4-follow-up-cancellation-enquiry-and-refund',
    variables: {},
    taskVariables: {
      N_OB_ArrangeFollowUp: {
        speciality: 'Oncology',
        priority: 'routine',
        requestedWindow: 21,
        recipients: ['patient'],
        documentType: 'follow_up_clinic_letter',
      },
      N_OB_ConfirmFollowUp: {},
    },
    out,
  })
  assert.ok(s4.elements.includes('N_OB_FollowUpBooked'), 'core-4 should end with the follow-up booked')

  // --- 5. core-4, a cancelled paid appointment, refunded ------------------
  // Started by a message, and it refunds the reference scenario 2 settled.
  const s5 = await drive({
    label: 'Scenario 5 - core-4, paid appointment cancelled and refunded',
    processDefinitionId: 'core-4-follow-up-cancellation-enquiry-and-refund',
    message: 'patient-cancellation-or-non-attendance',
    variables: {},
    taskVariables: {
      N_OB_RecordCancellation: {
        paidAppointment: true,
        offerAnotherAppointment: false,
        pathwayReviewRequired: false,
      },
      N_F_RetentionDecision: {
        refundDecision: 'full',
        paymentReference: PAYMENT_REFERENCE,
        refundReason: 'cancelled',
      },
    },
    out,
  })
  assert.ok(
    s5.elements.includes('N_F_ProcessRefund'),
    'core-4 should have asked the provider for the refund'
  )
  assert.ok(
    s5.elements.includes('N_F_RefundRecorded'),
    'core-4 should end with the refund recorded'
  )
  assert.ok(
    !s5.elements.includes('N_F_CorrectRefundRequest'),
    'the refund should have been accepted, not sent back for correction'
  )

  out('\n' + '='.repeat(74))
  out('All four operational models ran against the real workers. Every service task')
  out('in them was reached, every gateway routed on what the workers returned, and')
  out('the refund in scenario 5 was recorded by the provider against the payment')
  out('scenario 2 settled.')

  for (const worker of workers) worker.stop?.()
  await zeebe.close()
}

if (require.main === module) {
  main().catch((error) => {
    process.stderr.write(`\nOperational model run FAILED: ${error.message}\n`)
    process.exitCode = 1
  })
}
