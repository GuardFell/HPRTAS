/**
 * External workers for the Hospital Patient Referral, Treatment and
 * Administration System.
 *
 * Start them with `npm start`. They connect to the Zeebe gRPC gateway, register
 * one worker per service task in the operational model, and log every job with
 * the process instance and the outcome so the log can be used as test evidence.
 *
 * `npm run check` validates the configuration and the wiring without connecting
 * to an engine, which is useful before a demonstration.
 */

const { Camunda8 } = require('@camunda8/sdk')

const { loadConfig, connectionConfig } = require('./config')
const { createLogger } = require('./logger')
const { createServices } = require('./services')
const { applyOutcome, isOutcome, technicalFailure } = require('./outcome')

const WORKER_MODULES = [
  require('./workers/referral-validation'),
  require('./workers/appointment-availability'),
  require('./workers/treatment-availability'),
  require('./workers/payment-processing'),
  require('./workers/correspondence-dispatch'),
]

/** Builds the parts a job handler needs, without any connection to an engine. */
function createRuntime(config, log) {
  const services = createServices(config)
  const logger = log.child({})
  return { services, logger }
}

/**
 * Wraps a worker's business handler in the job contract: it must return a job
 * action on every path, and an unexpected exception has to be reported rather
 * than left to stall the job.
 */
function createTaskHandler(workerModule, config, log, services) {
  const workerLog = log.child({ worker: workerModule.name, taskType: workerModule.taskType })

  return async function taskHandler(job) {
    const startedAt = Date.now()
    const context = {
      now: new Date(),
      instanceKey: job.processInstanceKey,
      services,
      config,
      log: workerLog,
    }

    try {
      const outcome = await workerModule.handle(job.variables, context)

      if (!isOutcome(outcome)) {
        throw new Error(`the handler returned ${JSON.stringify(outcome)} instead of a job outcome`)
      }

      const fields = {
        jobKey: job.key,
        processInstanceKey: job.processInstanceKey,
        elementId: job.elementId,
        durationMs: Date.now() - startedAt,
        outcome: outcome.kind,
        errorCode: outcome.errorCode,
      }

      if (outcome.kind === 'businessError') {
        workerLog.warn('job ended with a business error', fields)
      } else if (outcome.kind === 'technicalFailure') {
        workerLog.error('job ended with a technical failure', fields)
      } else {
        workerLog.info('job completed', fields)
      }

      return applyOutcome(job, outcome)
    } catch (error) {
      // Anything reaching here is a defect or an infrastructure problem, not a
      // business outcome. The job is failed so the broker retries it.
      workerLog.error('unhandled error while handling a job', {
        jobKey: job.key,
        processInstanceKey: job.processInstanceKey,
        elementId: job.elementId,
        durationMs: Date.now() - startedAt,
        error: error.message,
        stack: error.stack,
      })
      return applyOutcome(
        job,
        technicalFailure(`the worker could not handle the job: ${error.message}`)
      )
    }
  }
}

/** Validates the configuration and prints what would be registered. */
function check(config) {
  const log = createLogger({ level: 'error' })
  createRuntime(config, log)

  process.stdout.write(`Configuration loaded from ${config.workersDir}\n`)
  process.stdout.write(`  .env file present: ${config.dotEnvLoaded}\n`)
  process.stdout.write(`  Zeebe gRPC address: ${connectionConfig(config).ZEEBE_GRPC_ADDRESS}\n`)
  process.stdout.write(`  authentication: ${connectionConfig(config).CAMUNDA_AUTH_STRATEGY}\n`)
  process.stdout.write(`  log level: ${config.logging.level}\n`)
  process.stdout.write('Workers that would be registered:\n')
  for (const worker of WORKER_MODULES) {
    const settings = config.workers[worker.name]
    process.stdout.write(
      `  ${worker.name.padEnd(24)} job type ${settings.taskType.padEnd(30)} ` +
        `concurrency ${settings.maxJobsToActivate}, job timeout ${settings.timeoutMs} ms\n`
    )
  }
  process.stdout.write('Configuration and wiring are valid.\n')
}

async function main() {
  const config = loadConfig()

  if (process.argv.includes('--check')) {
    check(config)
    return
  }

  const log = createLogger({ level: config.logging.level, file: config.logging.file })
  const { services } = createRuntime(config, log)

  const connection = connectionConfig(config)
  log.info('starting external workers', {
    zeebeGrpcAddress: connection.ZEEBE_GRPC_ADDRESS,
    authStrategy: connection.CAMUNDA_AUTH_STRATEGY,
    dotEnvLoaded: config.dotEnvLoaded,
  })

  const camunda = new Camunda8(connection)
  const zeebe = camunda.getZeebeGrpcApiClient()

  const registered = WORKER_MODULES.map((workerModule) => {
    const settings = config.workers[workerModule.name]
    const workerLog = log.child({ worker: workerModule.name })

    const worker = zeebe.createWorker({
      taskType: settings.taskType,
      taskHandler: createTaskHandler(workerModule, config, log, services),
      maxJobsToActivate: settings.maxJobsToActivate,
      timeout: settings.timeoutMs,
      id: `${workerModule.name}-${process.pid}`,
      onReady: () => workerLog.info('worker is ready to receive jobs'),
      onConnectionError: (error) =>
        workerLog.error('worker lost the connection to the gateway', {
          error: error instanceof Error ? error.message : String(error),
        }),
    })

    return { worker, settings, workerModule }
  })

  log.info('workers registered', {
    jobTypes: registered.map((entry) => entry.settings.taskType),
    processId: process.pid,
  })
  process.stdout.write(
    `\nHPRTAS external workers are running (${registered.length} workers). Press Ctrl-C to stop.\n` +
      registered.map((entry) => `  ${entry.settings.taskType}\n`).join('')
  )

  let stopping = false
  const shutdown = async (signal) => {
    if (stopping) return
    stopping = true
    log.info('stopping workers', { signal })
    await zeebe.close()
    await log.close()
    process.stdout.write('\nWorkers stopped.\n')
    process.exit(0)
  }

  process.on('SIGINT', () => shutdown('SIGINT'))
  process.on('SIGTERM', () => shutdown('SIGTERM'))

  // Keep the process alive while the workers poll.
  await new Promise(() => {})
}

if (require.main === module) {
  main().catch(async (error) => {
    process.stderr.write(`\nThe workers could not start: ${error.message}\n`)
    process.exitCode = 1
  })
}

module.exports = { createTaskHandler, WORKER_MODULES, main }
