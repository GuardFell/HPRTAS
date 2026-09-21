/**
 * Logging for the external workers.
 *
 * Every line is a JSON object with the correlation details the test evidence
 * needs: the worker, the job key, the process instance and the outcome. The
 * worker log is one of the artefacts the definition of done asks for, so the
 * lines are written for a reader, not only for a debugger.
 */

const fs = require('node:fs')
const path = require('node:path')

const LEVELS = { error: 0, warn: 1, info: 2, debug: 3 }

function createLogger({ level = 'info', file = null, name = 'workers' } = {}) {
  const threshold = LEVELS[level] ?? LEVELS.info
  let stream = null

  if (file) {
    fs.mkdirSync(path.dirname(file), { recursive: true })
    stream = fs.createWriteStream(file, { flags: 'a' })
  }

  function write(levelName, message, fields) {
    if (LEVELS[levelName] > threshold) return
    const line = JSON.stringify({
      time: new Date().toISOString(),
      level: levelName,
      logger: name,
      message,
      ...fields,
    })
    if (levelName === 'error') process.stderr.write(`${line}\n`)
    else process.stdout.write(`${line}\n`)
    if (stream) stream.write(`${line}\n`)
  }

  return {
    error: (message, fields = {}) => write('error', message, fields),
    warn: (message, fields = {}) => write('warn', message, fields),
    info: (message, fields = {}) => write('info', message, fields),
    debug: (message, fields = {}) => write('debug', message, fields),
    /** A logger carrying extra fields on every line, for example the worker name. */
    child(extra) {
      return {
        error: (message, fields = {}) => write('error', message, { ...extra, ...fields }),
        warn: (message, fields = {}) => write('warn', message, { ...extra, ...fields }),
        info: (message, fields = {}) => write('info', message, { ...extra, ...fields }),
        debug: (message, fields = {}) => write('debug', message, { ...extra, ...fields }),
      }
    },
    async close() {
      if (!stream) return
      await new Promise((resolve) => stream.end(resolve))
    },
  }
}

module.exports = { createLogger }
