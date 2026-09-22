package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;

import java.util.List;
import java.util.Map;

/**
 * Worker: referral-validation   (job type {@code validate-referral})
 *
 * Checks whether a referral carries the supporting information it is expected to carry, and returns
 * what has to be requested from the referring organisation when it does not (FR-002, EX-02, TC-03).
 *
 * The check is administrative on purpose. Medical Secretaries may not assess clinical suitability
 * and may not decide whether a referral is accepted (BR-01), so this worker only reports completeness
 * and never returns anything that looks like a clinical opinion. The output is checked for that in
 * {@code ReferralValidationTest}.
 */
public final class ReferralValidation implements WorkerModule {

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "documentsComplete", FieldSpec.bool().required(),
          "missingItems", FieldSpec.list().defaultTo(List.of()),
          "referringOrganisation", FieldSpec.identifier().required());

  @Override
  public String name() {
    return "referral-validation";
  }

  @Override
  public String taskType() {
    return "validate-referral";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the referral cannot be checked because the information is incomplete or unusable: "
              + check.summarise(),
          Vars.of("validationResult", "invalid_input"));
    }

    final String referringOrganisation = (String) check.values().get("referringOrganisation");
    final List<Object> requestedItems = Validate.asList(check.values().get("missingItems"));

    if (Boolean.TRUE.equals(check.values().get("documentsComplete"))) {
      if (!requestedItems.isEmpty()) {
        return Outcome.businessError(
            ErrorCode.INVALID_VARIABLE,
            "the referral is marked as complete but also lists missing items, so the documentation"
                + " check cannot be decided",
            Vars.of("validationResult", "invalid_input"));
      }
      return Outcome.completed(
          Vars.of("validationResult", "complete", "requestedItems", List.of()));
    }

    // The referral is incomplete but nothing says what is missing, so there is nothing to request
    // from the referring organisation and the process would otherwise continue with no next step
    // (EX-02).
    if (requestedItems.isEmpty()) {
      return Outcome.businessError(
          ErrorCode.MISSING_INFORMATION_NOT_SPECIFIED,
          "the referral from "
              + referringOrganisation
              + " is incomplete but no missing items were recorded, so nothing can be requested",
          Vars.of(
              "validationResult",
              "incomplete_without_items",
              "referringOrganisation",
              referringOrganisation));
    }

    return Outcome.completed(
        Vars.of(
            "validationResult", "incomplete",
            "requestedItems", requestedItems,
            "missingInformationRequestedFrom", referringOrganisation,
            "missingInformationRequestedDate", Validate.toIsoDate(context.now())));
  }
}
