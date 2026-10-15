package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.db.Database;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Worker: referral-validation   (job type {@code validate-referral})
 *
 * Checks whether a referral carries the supporting information it is expected to carry, and returns
 * what has to be requested from the referring organisation when it does not (FR-002, EX-02, TC-03).
 *
 * It also registers the referral in the hospital's records and checks the patient against the
 * register by their NHS number, which is the identification rule the case asks for when information
 * arrives from another organisation (BR-46, NFR-004, FR-048):
 *
 * <ul>
 *   <li>the number has to be ten digits. The form rejects anything else as well, and this is the
 *       second check, because a form can be bypassed and a worker cannot.</li>
 *   <li>a patient the register already holds is reported back with the name it holds for them: a
 *       referral whose patient name disagrees with the register is flagged rather than silently
 *       creating a second record.</li>
 *   <li>a patient the register does not hold is added to it, and the referral is recorded against
 *       them. The count of referrals already held for the patient is returned so the record of how
 *       many times this patient has been referred is visible to the process.</li>
 * </ul>
 *
 * The check is administrative on purpose. Medical Secretaries may not assess clinical suitability
 * and may not decide whether a referral is accepted (BR-01), so this worker only reports completeness
 * and identification and never returns anything that looks like a clinical opinion.
 */
public final class ReferralValidation implements WorkerModule {

  /** An NHS number is ten digits (BR-46). */
  public static final String NHS_NUMBER_PATTERN = "\\d{10}";

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "documentsComplete", FieldSpec.bool().required(),
          "missingItems", FieldSpec.list().defaultTo(List.of()),
          "referringOrganisation", FieldSpec.identifier().required(),
          // The referral and the patient it is for, so the hospital's own records can be written and
          // the patient checked against the register. The referral form supplies all three.
          "referralId", FieldSpec.identifier().required(),
          "patientId", FieldSpec.identifier().required(),
          "nhsNumber", FieldSpec.string(),
          "patientName", FieldSpec.string());

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

    final String referralId = (String) check.values().get("referralId");
    final String patientId = (String) check.values().get("patientId");
    final String nhsNumber = trimmed((String) check.values().get("nhsNumber"));
    final String patientName = trimmed((String) check.values().get("patientName"));
    final String referringOrganisation = (String) check.values().get("referringOrganisation");
    final List<Object> requestedItems = Validate.asList(check.values().get("missingItems"));

    // The identification check comes first: a referral that cannot be tied to a patient is not
    // checked further, because every record it would write is ambiguous (NFR-004).
    if (nhsNumber != null && !nhsNumber.matches(NHS_NUMBER_PATTERN)) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the NHS number \""
              + nhsNumber
              + "\" is not ten digits, so the patient cannot be identified against the register",
          Vars.of("validationResult", "invalid_patient_identification"));
    }

    if (Boolean.TRUE.equals(check.values().get("documentsComplete")) && !requestedItems.isEmpty()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the referral is marked as complete but also lists missing items, so the documentation"
              + " check cannot be decided",
          Vars.of("validationResult", "invalid_input"));
    }

    if (!Boolean.TRUE.equals(check.values().get("documentsComplete")) && requestedItems.isEmpty()) {
      // The referral is incomplete but nothing says what is missing, so there is nothing to request
      // from the referring organisation and the process would otherwise continue with no next step
      // (EX-02).
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

    final Database database = context.services().database();
    final Identification patient =
        identifyPatient(database, patientId, nhsNumber, patientName, context);

    final String validationResult =
        Boolean.TRUE.equals(check.values().get("documentsComplete")) ? "complete" : "incomplete";

    database.saveReferral(
        new Database.Referral(
            referralId,
            patient.patientId(),
            nhsNumber,
            referringOrganisation,
            Validate.toIsoDate(context.now()),
            Boolean.TRUE.equals(check.values().get("documentsComplete")),
            requestedItems.isEmpty() ? null : requestedItems.toString(),
            validationResult,
            Validate.toIsoDate(context.now())));

    final Map<String, Object> outputs =
        Vars.of(
            "patientRecordFound", patient.recordFound(),
            "patientDetailsMatch", patient.detailsMatch(),
            "patientName", patient.name(),
            "patientPreviousReferrals", patient.previousReferrals(),
            "patientChannelPreference", patient.channelPreference(),
            "patientId", patient.patientId(),
            "patientIdentifierConflict", patient.identifierConflict());

    context
        .log()
        .info(
            "referral checked against the patient register",
            Vars.of(
                "referralId", referralId,
                "patientId", patientId,
                "patientRecordFound", patient.recordFound(),
                "patientDetailsMatch", patient.detailsMatch(),
                "patientIdentifierConflict", patient.identifierConflict(),
                "validationResult", validationResult));

    if ("complete".equals(validationResult)) {
      outputs.put("validationResult", "complete");
      outputs.put("requestedItems", List.of());
    } else {
      outputs.put("validationResult", "incomplete");
      outputs.put("requestedItems", requestedItems);
      outputs.put("missingInformationRequestedFrom", referringOrganisation);
      outputs.put("missingInformationRequestedDate", Validate.toIsoDate(context.now()));
    }
    return Outcome.completed(outputs);
  }

  /** What the register said about the patient, and what was written to it. */
  private record Identification(
      boolean recordFound,
      boolean detailsMatch,
      String name,
      String channelPreference,
      int previousReferrals,
      String patientId,
      boolean identifierConflict) {}

  /**
   * Checks the patient against the register by NHS number, then by the hospital's identifier, and
   * keeps the register up to date. A patient the register does not hold is added to it.
   */
  private Identification identifyPatient(
      Database database,
      String patientId,
      String nhsNumber,
      String suppliedName,
      WorkerContext context) {

    Optional<Database.Patient> known = Optional.empty();
    if (nhsNumber != null) {
      known = database.findPatientByNhsNumber(nhsNumber);
    }
    if (known.isEmpty()) {
      known = database.findPatient(patientId);
    }

    // The NHS number is the identity the referring organisation knows, so where it is already held
    // against a different hospital identifier, the record the hospital holds wins: the referral is
    // filed under it and the disagreement is reported. Writing a second record for one NHS number
    // is the duplicate the case warns about (BR-46, NFR-004), and it is refused by the register's
    // own index rather than being allowed to happen quietly.
    final boolean identifierConflict =
        known.isPresent() && !known.get().patientId().equals(patientId);
    final String effectivePatientId = identifierConflict ? known.get().patientId() : patientId;

    final String today = Validate.toIsoDate(context.now());
    final boolean recordFound = known.isPresent();
    final String recordedName = known.map(Database.Patient::patientName).orElse(null);
    final String recordedPreference = known.map(Database.Patient::channelPreference).orElse(null);
    final int previousReferrals = database.referralCount(effectivePatientId);

    // A name that disagrees with the register is flagged, not overwritten: which of the two is right
    // is for a person to say, and the register must not be quietly changed by an automated step.
    final boolean detailsMatch =
        !recordFound
            || suppliedName == null
            || recordedName == null
            || suppliedName.equalsIgnoreCase(recordedName);

    final String nameToKeep = detailsMatch ? firstNonNull(suppliedName, recordedName) : recordedName;

    database.savePatient(
        new Database.Patient(
            effectivePatientId,
            nhsNumber,
            nameToKeep,
            recordedPreference,
            recordFound ? known.get().firstSeen() : today,
            today,
            previousReferrals));

    return new Identification(
        recordFound, detailsMatch, nameToKeep, recordedPreference, previousReferrals, effectivePatientId,
        identifierConflict);
  }

  private static String trimmed(String value) {
    if (value == null) {
      return null;
    }
    final String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String firstNonNull(String first, String second) {
    return first != null ? first : second;
  }
}
