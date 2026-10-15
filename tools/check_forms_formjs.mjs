/**
 * The form check: imports every `.form` in `forms/` into @bpmn-io/form-js, the library Tasklist
 * renders forms with, and drives the validation of one form to prove the rules fire.
 *
 * Needs form-js and jsdom installed somewhere the loader can find:
 *
 *   npm install @bpmn-io/form-js jsdom --prefix <dir>
 *   NODE_PATH=<dir>/node_modules node tools/check_forms_formjs.mjs
 *
 * The same check the forms README describes: an import error is a task that renders no form at all,
 * and a validation rule that does not fire is a required field a user can submit empty.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { JSDOM } = require('jsdom');

const REPO = dirname(dirname(fileURLToPath(import.meta.url)));
const FORM_DIR = join(REPO, 'forms');

// The library reads the DOM at load time, so the window has to be in place before it is required.
const dom = new JSDOM('<!DOCTYPE html><html><body><div id="container"></div></body></html>');
global.window = dom.window;
global.document = dom.window.document;
// Node 22 defines navigator itself and will not let it be overwritten; the library only reads
// userAgent and platform, which the jsdom one provides.
Object.defineProperty(global, 'navigator', { value: dom.window.navigator, configurable: true });
for (const name of [
  'HTMLElement', 'HTMLInputElement', 'Node', 'Element', 'Event', 'CustomEvent', 'KeyboardEvent',
  'MouseEvent', 'FocusEvent', 'InputEvent', 'getComputedStyle', 'requestAnimationFrame',
  'cancelAnimationFrame', 'customElements', 'DOMParser', 'XMLSerializer', 'Image', 'Blob',
  'File', 'FileList', 'DataTransfer', 'DragEvent', 'CSSStyleSheet', 'ResizeObserver',
]) {
  global[name] = dom.window[name];
}

// The viewer is the half Tasklist renders with; the editor package is not needed and does not
// load outside a browser.
const { Form } = require('@bpmn-io/form-js-viewer');

/** Imports a form and returns its container, or throws with the importer's own message. */
function mount(schema, data = {}) {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const form = new Form({ container });
  form.importSchema(schema, data);
  return { form, container };
}

/** Every `.form` in `forms/` has to import: one that does not renders no form at all. */
function checkImport() {
  const failures = [];
  const files = readdirSync(FORM_DIR).filter((name) => name.endsWith('.form')).sort();
  for (const name of files) {
    const schema = JSON.parse(readFileSync(join(FORM_DIR, name), 'utf-8'));
    try {
      mount(schema);
    } catch (error) {
      failures.push(`${name}: ${error.message}`);
    }
  }
  console.log(`imported ${files.length - failures.length} of ${files.length} forms`);
  for (const failure of failures) {
    console.log(`  FAIL ${failure}`);
  }
  return failures;
}

/**
 * Imports, then runs every field through the validator the renderer itself uses.
 *
 * `Form.validate()` only reports the fields the renderer has mounted instances for, which is none
 * before anything is typed, so the harness calls the same validator per field and key instead. The
 * value it checks is the one the test supplied, which is what a user's entry would have left in the
 * submission.
 */
function validationMessages(schema, data) {
  const { form } = mount(schema, data);
  const validator = form.get('validator');
  const registry = form.get('formFieldRegistry');
  const messages = [];
  for (const field of registry.getAll()) {
    if (!field.key) {
      continue;
    }
    for (const message of validator.validateFieldInstance(field, data[field.key])) {
      messages.push(`${field.key}: ${message}`);
    }
  }
  return messages;
}

function validationProblems(check, schema, data, expected) {
  const joined = validationMessages(schema, data).join(' | ');
  if (!expected.test(joined)) {
    return `expected ${expected} but the form reported: ${joined || '(nothing)'}`;
  }
  return null;
}

function checkValidation() {
  const failures = [];
  const read = (name) => JSON.parse(readFileSync(join(FORM_DIR, name), 'utf-8'));

  const confirmation = read('appointment-confirmation.form');
  let problem = validationProblems(
    null, confirmation,
    { confirmationOutcome: 'confirmed', appointmentReference: 'APPT-2026-0001', patientId: 'PAT-1001',
      confirmationBasis: 'funding_in_place', confirmationDate: '2026-10-01', confirmedBy: 'A Clinician',
      recipients: 'patient', documentType: 'appointment_letter' },
    /^$/);
  if (problem) failures.push(`appointment-confirmation: a complete form should report nothing: ${problem}`);

  problem = validationProblems(
    null, confirmation,
    { confirmationOutcome: 'confirmed', appointmentReference: 'not a reference!!', patientId: '', confirmedBy: '' },
    /appointmentReference: Use the reference as the system writes it/);
  if (problem) failures.push(`appointment-confirmation: ${problem}`);

  problem = validationProblems(
    null, confirmation,
    { confirmationOutcome: 'declined_by_patient', appointmentReference: 'APPT-2026-0001', patientId: 'PAT-1001',
      confirmationBasis: 'not_applicable', confirmationDate: '2026-10-01', confirmedBy: '12345',
      recipients: 'patient', documentType: 'appointment_letter' },
    /confirmedBy: Enter a name or role that contains letters/);
  if (problem) failures.push(`appointment-confirmation: a numeric name should be refused: ${problem}`);

  const referralCheck = read('referral-check.form');
  problem = validationProblems(
    null, referralCheck,
    { referralId: 'REF-2026-0001', patientId: 'PAT-1001', nhsNumber: '94347659', patientName: 'Ada Lovelace',
      referringOrganisation: 'St Mary GP Surgery', patientIdentificationConfirmed: true, checkedBy: 'A Secretary' },
    /ten digits/);
  if (problem) failures.push(`referral-check: ${problem}`);

  const treatmentBooking = read('treatment-booking.form');
  const startDate = (treatmentBooking.components || [])
    .flatMap((component) => component.components || [component])
    .find((component) => component.key === 'treatmentStartDate');
  if (startDate && startDate.disallowPassedDates === true) {
    console.log('treatment-booking: future-dated start date is enforced by the picker');
  } else {
    failures.push('treatment-booking: treatmentStartDate should not offer past dates');
  }

  return failures;
}

const importFailures = checkImport();
const validationFailures = checkValidation();
const failures = [...importFailures, ...validationFailures];
if (failures.length) {
  console.log('');
  for (const failure of validationFailures) {
    console.log(`  FAIL ${failure}`);
  }
  console.log(`FAIL: ${failures.length} problem(s)`);
  process.exit(1);
}
console.log('validation rules fire as intended');
console.log('PASS: the forms import and validate');
