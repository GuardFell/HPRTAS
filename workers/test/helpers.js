/**
 * Shared test helpers.
 *
 * The tests exercise the worker handlers without a broker: a handler takes
 * variables and returns an outcome, which is the same code path the worker runs
 * in production. The simulated services run for real, with the artificial
 * latency switched off so the suite stays fast.
 */

const path = require('node:path')
const fs = require('node:fs')

const { createServices } = require('../src/services')

const DEFAULTS_FILE = path.join(__dirname, '..', 'config', 'workers.default.json')

/** Deep merge that is good enough for the configuration shape. */
function merge(base, override) {
  const result = { ...base }
  for (const [key, value] of Object.entries(override || {})) {
    const previous = result[key]
    const bothObjects =
      value && previous && typeof value === 'object' && typeof previous === 'object' &&
      !Array.isArray(value) && !Array.isArray(previous)
    result[key] = bothObjects ? merge(previous, value) : value
  }
  return result
}

/** A configuration built from the committed defaults, with no latency. */
function buildConfig(overrides = {}) {
  const defaults = JSON.parse(fs.readFileSync(DEFAULTS_FILE, 'utf8'))
  const config = merge(defaults, overrides)
  for (const service of Object.values(config.simulatedServices)) service.latencyMs = 0
  config.logging = { level: 'error', file: null }
  config.workersDir = path.join(__dirname, '..')
  config.businessCalendar = merge(defaults.businessCalendar, overrides.businessCalendar)
  return config
}

function silentLogger() {
  const noop = () => {}
  return { error: noop, warn: noop, info: noop, debug: noop, child: () => silentLogger() }
}

/**
 * A context of the same shape the worker builds from a real job. `now` is fixed
 * so the dates in the assertions do not move.
 */
function buildContext(config, { now = new Date('2026-09-16T09:00:00Z'), instanceKey = '2251799813685249' } = {}) {
  return {
    now,
    instanceKey,
    services: createServices(config),
    config,
    log: silentLogger(),
  }
}

/**
 * A stand-in for the SDK job object. It records which job action the worker
 * chose, so the tests can assert on the outcome as the broker would see it.
 */
function buildJob(variables) {
  return {
    key: '4503599627370497',
    type: 'test',
    processInstanceKey: '2251799813685249',
    elementId: 'ServiceTask_test',
    variables,
    action: null,
    complete(updatedVariables) {
      this.action = { kind: 'complete', variables: updatedVariables }
      return Promise.resolve('JOB_ACTION_ACKNOWLEDGEMENT')
    },
    error(failure) {
      this.action = { kind: 'error', ...failure }
      return Promise.resolve('JOB_ACTION_ACKNOWLEDGEMENT')
    },
    fail(failure) {
      this.action = { kind: 'fail', ...failure }
      return Promise.resolve('JOB_ACTION_ACKNOWLEDGEMENT')
    },
    forward() {
      this.action = { kind: 'forward' }
      return Promise.resolve('JOB_ACTION_ACKNOWLEDGEMENT')
    },
  }
}

module.exports = { buildConfig, buildContext, buildJob, silentLogger }
