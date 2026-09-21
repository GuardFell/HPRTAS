/**
 * Worker: referral-validation   (job type `validate-referral`)
 *
 * Checks whether a referral carries the supporting information it is expected
 * to carry, and returns what has to be requested from the referring
 * organisation when it does not (FR-002, EX-02, TC-03).
 *
 * The check is administrative on purpose. Medical Secretaries may not assess
 * clinical suitability and may not decide whether a referral is accepted
 * (BR-01), so this worker only reports completeness and never returns anything
 * that looks like a clinical opinion. The output is checked for that in
 * `test/workers.test.js`.
 */

const { completed, businessError } = require('../outcome')
const { ERROR_CODES, checkVariables, asList, toIsoDate } = require('../validate')

const INPUT_VARIABLES = {
  documentsComplete: { type: 'boolean', required: true },
  missingItems: { type: 'list', required: false, default: [] },
  referringOrganisation: { type: 'identifier', required: true },
}

async function handle(variables, { now }) {
  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `the referral cannot be checked because the information is incomplete or unusable: ${problems.join('; ')}`,
      { validationResult: 'invalid_input' }
    )
  }

  const requestedItems = asList(values.missingItems)

  if (values.documentsComplete) {
    if (requestedItems.length > 0) {
      return businessError(
        ERROR_CODES.INVALID_VARIABLE,
        'the referral is marked as complete but also lists missing items, so the documentation check cannot be decided',
        { validationResult: 'invalid_input' }
      )
    }
    return completed({ validationResult: 'complete', requestedItems: [] })
  }

  // The referral is incomplete but nothing says what is missing, so there is
  // nothing to request from the referring organisation and the process would
  // otherwise continue with no next step (EX-02).
  if (requestedItems.length === 0) {
    return businessError(
      ERROR_CODES.MISSING_INFORMATION_NOT_SPECIFIED,
      `the referral from ${values.referringOrganisation} is incomplete but no missing items were recorded, so nothing can be requested`,
      { validationResult: 'incomplete_without_items', referringOrganisation: values.referringOrganisation }
    )
  }

  return completed({
    validationResult: 'incomplete',
    requestedItems,
    missingInformationRequestedFrom: values.referringOrganisation,
    missingInformationRequestedDate: toIsoDate(now),
  })
}

module.exports = {
  name: 'referral-validation',
  taskType: 'validate-referral',
  inputVariables: INPUT_VARIABLES,
  handle,
}
