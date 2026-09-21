/**
 * Configuration for the external workers.
 *
 * Three layers, each overriding the one before it:
 *
 *   1. `config/workers.default.json`  - committed, the agreed defaults
 *   2. `config/workers.local.json`    - optional, never committed, per machine
 *   3. environment variables          - the `.env` file or the shell
 *
 * The connection settings are handed to the Camunda 8 Node SDK with the same
 * key names it reads from the environment, so there is one vocabulary for the
 * connection everywhere.
 */

const fs = require('node:fs')
const path = require('node:path')

const WORKERS_DIR = path.join(__dirname, '..')
const DEFAULTS_FILE = path.join(WORKERS_DIR, 'config', 'workers.default.json')
const LOCAL_FILE = path.join(WORKERS_DIR, 'config', 'workers.local.json')

/** Environment variables this module reads directly, with their defaults. */
const ENV_DEFAULTS = {
  HPRTAS_LOG_LEVEL: 'info',
  HPRTAS_LOG_FILE: '',
  HPRTAS_SIM_SCHEDULING: '',
  HPRTAS_SIM_TREATMENT: '',
  HPRTAS_SIM_PAYMENT: '',
  HPRTAS_SIM_CORRESPONDENCE: '',
}

/** Forced outcomes accepted per simulated service, with the input name that overrides them. */
const SERVICE_ENV = {
  scheduling: 'HPRTAS_SIM_SCHEDULING',
  treatment: 'HPRTAS_SIM_TREATMENT',
  payment: 'HPRTAS_SIM_PAYMENT',
  correspondence: 'HPRTAS_SIM_CORRESPONDENCE',
}

function readJsonFile(file, required) {
  if (!fs.existsSync(file)) {
    if (required) throw new Error(`Configuration file is missing: ${file}`)
    return {}
  }
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'))
  } catch (error) {
    throw new Error(`Configuration file is not valid JSON: ${file} (${error.message})`)
  }
}

/**
 * Loads `.env` from the workers directory when it exists. Uses the Node built-in
 * loader so the project needs no dependency for configuration.
 */
function loadDotEnv() {
  const file = path.join(WORKERS_DIR, '.env')
  if (!fs.existsSync(file)) return false
  process.loadEnvFile(file)
  return true
}

function deepMerge(base, override) {
  const result = { ...base }
  for (const [key, value] of Object.entries(override || {})) {
    const previous = result[key]
    const bothPlainObjects =
      value !== null &&
      previous !== null &&
      typeof value === 'object' &&
      typeof previous === 'object' &&
      !Array.isArray(value) &&
      !Array.isArray(previous)
    result[key] = bothPlainObjects ? deepMerge(previous, value) : value
  }
  return result
}

function loadConfig() {
  const dotEnvLoaded = loadDotEnv()

  let config = deepMerge(readJsonFile(DEFAULTS_FILE, true), readJsonFile(LOCAL_FILE, false))

  // Environment overrides for the simulated services. An empty string means
  // "not set", so leaving a commented line in `.env` changes nothing.
  for (const [service, envName] of Object.entries(SERVICE_ENV)) {
    const forced = (process.env[envName] || '').trim()
    if (forced) config.simulatedServices[service].outcome = forced
  }

  const logLevel = (process.env.HPRTAS_LOG_LEVEL || ENV_DEFAULTS.HPRTAS_LOG_LEVEL).trim()
  const logFile = (process.env.HPRTAS_LOG_FILE || ENV_DEFAULTS.HPRTAS_LOG_FILE).trim()

  config.logging = {
    level: logLevel,
    file: logFile ? path.resolve(WORKERS_DIR, logFile) : null,
  }
  config.dotEnvLoaded = dotEnvLoaded
  config.workersDir = WORKERS_DIR

  validateConfig(config)
  return config
}

const LOG_LEVELS = ['error', 'warn', 'info', 'debug']

/** Fails fast with a readable message rather than letting the SDK fail later. */
function validateConfig(config) {
  const problems = []

  if (!LOG_LEVELS.includes(config.logging.level)) {
    problems.push(
      `HPRTAS_LOG_LEVEL is "${config.logging.level}" but must be one of ${LOG_LEVELS.join(', ')}`
    )
  }

  for (const [name, worker] of Object.entries(config.workers)) {
    if (!worker.taskType) problems.push(`workers.${name} has no taskType`)
    if (!Number.isInteger(worker.maxJobsToActivate) || worker.maxJobsToActivate < 1) {
      problems.push(`workers.${name}.maxJobsToActivate must be a positive integer`)
    }
    if (!Number.isInteger(worker.timeoutMs) || worker.timeoutMs < 1000) {
      problems.push(`workers.${name}.timeoutMs must be at least 1000 ms`)
    }
  }

  const addresses = [
    config.connection.ZEEBE_GRPC_ADDRESS,
    process.env.ZEEBE_GRPC_ADDRESS,
    process.env.ZEEBE_ADDRESS,
  ].filter(Boolean)
  if (addresses.length > 0 && !/^(grpc|grpcs):\/\//.test(String(addresses[0]))) {
    problems.push(
      `the Zeebe gRPC address "${addresses[0]}" must include its protocol, for example grpc://localhost:26500`
    )
  }

  if (problems.length > 0) {
    throw new Error(`Invalid configuration:\n  - ${problems.join('\n  - ')}`)
  }
}

/** The connection settings handed to the Camunda 8 Node SDK. */
function connectionConfig(config) {
  const connection = { ...config.connection }
  for (const key of Object.keys(connection)) {
    if (process.env[key]) connection[key] = process.env[key]
  }
  return connection
}

module.exports = { loadConfig, connectionConfig, WORKERS_DIR }
