/**
 * The simulated external services, built once and shared by every worker.
 *
 * They are constructed from the `simulatedServices` section of the
 * configuration, so the whole set can be pointed at a different outcome without
 * touching the code.
 */

const { createSchedulingService } = require('./scheduling-service')
const { createTreatmentService } = require('./treatment-service')
const { createPaymentServiceProvider } = require('./payment-service-provider')
const { createCorrespondenceService } = require('./correspondence-service')

function createServices(config) {
  return {
    scheduling: createSchedulingService(config.simulatedServices.scheduling),
    treatment: createTreatmentService(config.simulatedServices.treatment),
    payment: createPaymentServiceProvider(config.simulatedServices.payment),
    correspondence: createCorrespondenceService(config.simulatedServices.correspondence),
  }
}

module.exports = { createServices }
