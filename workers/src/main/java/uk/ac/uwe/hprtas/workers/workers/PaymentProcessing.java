package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.services.PaymentServiceProvider;

import java.util.List;
import java.util.Map;

/**
 * Worker: payment-processing   (job type {@code process-payment})
 *
 * Sends the payment request to the external Payment Service Provider and records what came back.
 *
 * The rules this worker enforces:
 *
 * <ul>
 *   <li>Card and security details never reach the hospital system. If any are present in the
 *       variables the worker refuses the payment outright rather than passing them on (BR-06,
 *       NFR-007, AC-09).</li>
 *   <li>A patient whose treatment is funded by the hospital or an approved insurer, or who holds an
 *       exemption, is not asked to pay: the provider is not called at all.</li>
 *   <li>The same payment reference is never charged twice. A second attempt returns the original
 *       transaction marked as a duplicate (FR-023, EX-08, AC-10).</li>
 *   <li>A payment taken without a returned confirmation is marked for investigation and is not
 *       re-requested (BR-07, EX-09, TC-09, AC-04).</li>
 * </ul>
 */
public final class PaymentProcessing implements WorkerModule {

  /** Funding routes where the patient makes no direct payment (AS-08, AS-09). */
  public static final List<String> ROUTES_WITHOUT_PATIENT_PAYMENT =
      List.of("hospital", "insurer", "exempt");

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "chargeAmount", FieldSpec.money().required().max(100000),
          "fundingRoute",
              FieldSpec.string().required().oneOf("hospital", "insurer", "patient", "exempt"),
          "paymentReference", FieldSpec.identifier().required(),
          "paymentOutcome",
              FieldSpec.string()
                  .oneOf(
                      PaymentServiceProvider.COMPLETED,
                      PaymentServiceProvider.DECLINED,
                      PaymentServiceProvider.SUCCESS_NO_CONFIRMATION));

  @Override
  public String name() {
    return "payment-processing";
  }

  @Override
  public String taskType() {
    return "process-payment";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final List<String> prohibited = Validate.findProhibitedFinancialFields(variables);
    if (!prohibited.isEmpty()) {
      return Outcome.businessError(
          ErrorCode.PROHIBITED_FINANCIAL_DATA,
          "the payment cannot be processed because card or security details were supplied: "
              + String.join(", ", prohibited)
              + ". The hospital system must not hold them.",
          Vars.of(
              "paymentStatus", "not_processed", "prohibitedFields", List.copyOf(prohibited)));
    }

    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the payment cannot be processed because the request is incomplete or unusable: "
              + check.summarise(),
          Vars.of("paymentStatus", "not_processed"));
    }

    final String fundingRoute = (String) check.values().get("fundingRoute");

    if (ROUTES_WITHOUT_PATIENT_PAYMENT.contains(fundingRoute)) {
      context
          .log()
          .info(
              "no patient payment is required for this funding route",
              Vars.of("fundingRoute", fundingRoute));
      return Outcome.completed(
          Vars.of(
              "paymentStatus", "not_required",
              "fundingRoute", fundingRoute,
              "transactionReference", null,
              "paymentDate", null,
              "paidAmount", null,
              "confirmationReceived", false,
              "requiresInvestigation", false));
    }

    final PaymentServiceProvider.PaymentResult result =
        context
            .services()
            .payment()
            .requestPayment(
                (String) check.values().get("paymentReference"),
                (Number) check.values().get("chargeAmount"),
                (String) check.values().get("paymentOutcome"),
                context.now());

    context
        .log()
        .info(
            "payment request returned",
            Vars.of(
                "paymentStatus", result.status(),
                "transactionReference", result.transactionReference(),
                "confirmationReceived", result.confirmationReceived(),
                "requiresInvestigation", result.requiresInvestigation(),
                "duplicateAttempt", result.duplicateAttempt()));

    return Outcome.completed(
        Vars.of(
            "paymentStatus", result.status(),
            "fundingRoute", fundingRoute,
            "transactionReference", result.transactionReference(),
            "paymentDate", result.paymentDate(),
            "paidAmount", result.paidAmount(),
            "confirmationReceived", result.confirmationReceived(),
            "requiresInvestigation", result.requiresInvestigation(),
            "duplicateAttempt", result.duplicateAttempt()));
  }
}
