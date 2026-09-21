/**
 * Input validation for the workers.
 *
 * The process variables come from Camunda Forms and from earlier steps in the
 * process, so a worker cannot assume they are present or well formed. Every
 * worker checks its own inputs before doing any work, and an unusable input is
 * reported as a business error rather than allowed to throw: the requirement is
 * a controlled outcome, with no rebooking and no recharging.
 */

/** Business error codes. They are the contract with the model's error catch events. */
const ERROR_CODES = {
  INVALID_VARIABLE: 'INVALID_VARIABLE',
  MISSING_INFORMATION_NOT_SPECIFIED: 'MISSING_INFORMATION_NOT_SPECIFIED',
  PROHIBITED_FINANCIAL_DATA: 'PROHIBITED_FINANCIAL_DATA',
  UNAUTHORISED_BOOKING_REQUEST: 'UNAUTHORISED_BOOKING_REQUEST',
}

/**
 * Field specifications:
 *   type      string | boolean | number | money | days | identifier | list
 *   required  the process cannot continue without it
 *   oneOf     the accepted values, for the values the process branches on
 *   max       upper bound for numbers and for list length
 */
function checkField(name, value, spec) {
  const problems = []
  const present = value !== undefined && value !== null && value !== ''

  if (!present) {
    if (spec.required) problems.push(`${name} is required but was not supplied`)
    return { problems, value: spec.default }
  }

  switch (spec.type) {
    case 'string':
      if (typeof value !== 'string') problems.push(`${name} must be text`)
      else if (spec.oneOf && !spec.oneOf.includes(value)) {
        problems.push(`${name} is "${value}" but must be one of ${spec.oneOf.join(', ')}`)
      }
      break

    case 'boolean':
      if (typeof value !== 'boolean') {
        problems.push(`${name} must be true or false, but was ${JSON.stringify(value)}`)
      }
      break

    case 'number':
    case 'money':
      if (typeof value !== 'number' || !Number.isFinite(value)) {
        problems.push(`${name} must be a number, but was ${JSON.stringify(value)}`)
      } else if (spec.type === 'money' && value <= 0) {
        problems.push(`${name} must be greater than zero, but was ${value}`)
      } else if (spec.max !== undefined && value > spec.max) {
        problems.push(`${name} must not be more than ${spec.max}, but was ${value}`)
      }
      break

    case 'days':
      if (!Number.isInteger(value) || value < 1) {
        problems.push(`${name} must be a whole number of days of at least 1, but was ${JSON.stringify(value)}`)
      } else if (value > 366) {
        problems.push(`${name} must not be more than 366 days, but was ${value}`)
      }
      break

    case 'identifier':
      if (typeof value !== 'string' || value.trim() === '') {
        problems.push(`${name} must be a non-empty reference`)
      }
      break

    case 'list': {
      const list = asList(value)
      if (list.length === 0) problems.push(`${name} must not be empty`)
      else if (spec.max !== undefined && list.length > spec.max) {
        problems.push(`${name} must not contain more than ${spec.max} entries, but had ${list.length}`)
      }
      break
    }

    default:
      problems.push(`${name} has an unknown specification type ${spec.type}`)
  }

  return { problems, value }
}

/** Runs every specification and collects the problems into one message. */
function checkVariables(variables, specifications) {
  const problems = []
  const values = {}

  for (const [name, spec] of Object.entries(specifications)) {
    const result = checkField(name, variables[name], spec)
    problems.push(...result.problems)
    values[name] = result.value
  }

  return { problems, values }
}

/** Accepts a comma-separated string from a form field or a real array. */
function asList(value) {
  if (Array.isArray(value)) return value.filter((entry) => String(entry).trim() !== '')
  if (typeof value === 'string') {
    return value
      .split(',')
      .map((entry) => entry.trim())
      .filter((entry) => entry !== '')
  }
  return []
}

/**
 * Card and security details must never reach the hospital system, so their
 * presence is treated as a defective input rather than something to ignore and
 * pass on (BR-06, NFR-007). The list is intentionally wider than the fields a
 * form would use.
 */
const PROHIBITED_FINANCIAL_FIELDS = [
  'cardNumber',
  'cardnumber',
  'card_number',
  'pan',
  'cvv',
  'cvc',
  'cv2',
  'securityCode',
  'cardSecurityCode',
  'cardExpiry',
  'expiryDate',
  'cardHolderName',
  'magstripe',
  'trackData',
]

/** @returns the prohibited field names found in the variables. */
function findProhibitedFinancialFields(variables) {
  return PROHIBITED_FINANCIAL_FIELDS.filter(
    (field) => variables[field] !== undefined && variables[field] !== null && variables[field] !== ''
  )
}

/** ISO calendar date (YYYY-MM-DD), which is the format used for every business date. */
function toIsoDate(date) {
  return date.toISOString().slice(0, 10)
}

function addDays(date, days) {
  const result = new Date(date.getTime())
  result.setUTCDate(result.getUTCDate() + days)
  return result
}

/** Whole days from `from` to `to`, counted on calendar dates. */
function daysBetween(from, to) {
  const start = Date.UTC(from.getUTCFullYear(), from.getUTCMonth(), from.getUTCDate())
  const end = Date.UTC(to.getUTCFullYear(), to.getUTCMonth(), to.getUTCDate())
  return Math.round((end - start) / 86400000)
}

module.exports = {
  ERROR_CODES,
  checkVariables,
  asList,
  findProhibitedFinancialFields,
  toIsoDate,
  addDays,
  daysBetween,
}
