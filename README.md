# Hospital Patient Referral, Treatment and Administration System (HPRTAS)

HPRTAS is a hospital patient administration system covering the pathway from a referral arriving
from a general practitioner or another hospital, through the clinical decision, the funding and
payment of treatment, the treatment bookings themselves, the clinic letters that follow, and the
follow-up, cancellation, enquiry and refund handling that comes after. It is built as a set of
executable BPMN processes on Camunda 8, with external workers carrying out the automated steps
that talk to systems outside the hospital.

The repository holds the models, the forms the staff use, the workers, the documentation and the
test evidence for that system.

## The version this describes

The submitted version is tagged twice on one commit, and this file is committed in it:
**`submission-2026-09-29`**, for the submission, and **`release-2.0`**, for the second release.
`git rev-parse --short submission-2026-09-29` prints the commit. `release-1.0` names the
first release, which is an earlier version - it resolves to `69e01a1`, four models and 40 forms - and
it is left where it is, because the results recorded against it are only readable there. Every result
in `tests/evidence/` names the commit it was produced at, so a claim can be traced to the version it
belongs to rather than to the tree as it stands.

## What is in the repository

`models/` holds the BPMN. `models/strategic/` contains the high-level view of the wider process,
`models/socio-technical/` the i\* Strategic Dependency and Strategic Rationale models, and
`models/operational/` the five executable processes that Camunda runs.

`forms/` holds the Camunda Forms the user tasks are bound to, one `.form` file per form, each with
the form key the model refers to.

`workers/` holds the external workers as a Maven project: the job workers themselves, the
simulated external services they call, and their configuration.

`docs/` holds everything that describes the project rather than implements it: the case study
summary, the requirements and their traceability, and the backlog, planning and working agreements
that go with delivering it.

`tests/` holds the test plan and the evidence of the runs.

`tools/` holds the scripts that redraw the diagrams, export them and check that the models, the
forms and the workers agree with each other, so that what is claimed here can be reproduced rather
than taken on trust. `tools/README.md` says what each one does and what it needs.

`Enterprise Architecture/` holds the portfolio deliverable describing the enterprise and its
information systems, and the Zachman Framework view of them.

`HPRTAS - Five Executable Processes.pptx` is the presentation deck for the second release: the five
executable processes one to a slide, and the ten acceptance criteria with the scenario and the
result recorded for each. Its footers name the version its claims belong to.

## Running it

The processes run on Camunda 8 Run 8.9.19, driven through the Orchestration Cluster API on `/v2/`.
It needs Java 21: the runtime is bundled in `camunda-runtime\jdk-21`, and JDK 24 or newer is not
supported by Camunda 8.9.

Start the engine with `camunda-runtime\start-camunda.bat`; the first start takes about a minute.
Stop it with `camunda-runtime\stop-camunda.bat`. Operate and Tasklist are then on
http://localhost:8080/operate and http://localhost:8080/tasklist, both with the login `demo` /
`demo`. Camunda Modeler is at `camunda-runtime\camunda-modeler\Camunda Modeler.exe`; start the
engine before opening it so it detects the connection.

Deploy the models and the forms together — a model points at its forms by key, so Tasklist needs
both to resolve them:

```bash
cd models/operational
for f in *.bpmn; do
  curl -X POST "http://localhost:8080/v2/deployments" -F "resources=@$f;type=application/xml"
done
cd ../../forms
for f in *.form; do
  curl -X POST "http://localhost:8080/v2/deployments" -F "resources=@$f;type=application/json"
done
```

Every model in `models/operational/` is deployed this way, and every form in `forms/` goes with
them, so no task is left pointing at a form key nothing resolves.
`python tools/verify_hprtas_engine_forms.py` does the same thing and then proves it: it deploys each
model with its forms, starts it, and checks that the user task it reaches resolves its form.

The workers are a separate Maven project. Copy `workers/.env.example` to `workers/.env` first, then:

```bash
cd workers
mvn compile exec:java
```

They need Java 21, which is the runtime the engine uses. `mvn compile exec:java -Dexec.args="--check"`
validates the configuration and the wiring without connecting to the engine, which is useful before
a demonstration.

The five operational processes are separate, so each is started separately from Tasklist under
Processes. Complete the user tasks on an instance as it reaches them; each task is assigned to the
candidate group its lane represents. `core-4-follow-up-cancellation-enquiry-and-refund` also has
two message start events, for a cancellation arriving and for a patient enquiry arriving, which are
started by sending that message rather than from the Processes page:

```bash
cd workers
mvn compile exec:java -Dexec.args="--publish-message patient-cancellation-or-non-attendance"
mvn compile exec:java -Dexec.args="--publish-message patient-enquiry"
```

Neither path is requested by the hospital, so neither can be an ordinary start event, and a process
can hold only one of those. The command names the message the model waits for; see
`workers/README.md` under *Starting a process by message* for what it does and does not prove.

`core-5-missing-information-message-exchange` is the other way round: it waits for a message. Start
it from the Processes page with the referral reference on the instance, complete the recording task,
and it will send the request and then wait at the catch event until the reply arrives. Send the reply
with the correlation key, which is what addresses it to that referral:

```bash
cd workers
mvn compile exec:java -Dexec.args="--publish-message missing-information-supplied --correlation-key REF-2026-0001"
```

Without the key the publication is accepted and correlated with nothing, so the instance stays where
it is. `workers/README.md` under *Publishing a message* has the two kinds of message side by side.

The strategic model is a non-executable view of the process. Do not deploy it: Camunda rejects a
deployment that contains no executable process.

## Deployment configuration

Everything the engine runs is deployed from this repository: the five models in
`models/operational/`, each with the forms its user tasks resolve. Nothing is configured inside the
engine itself, so the configuration is the files below and the commands above. The strategic model
and the socio-technical views are not deployed, for the reason just given.

| What | Value | Where it is set |
|---|---|---|
| Engine | Camunda 8 Run 8.9.19 | the bundled runtime, `camunda-runtime\c8run-8.9.19\` |
| Orchestration Cluster API | `http://localhost:8080/v2/`, every call a `POST` with a JSON body | the engine's own ports; nothing here sets them |
| Zeebe gRPC gateway | `grpc://localhost:26500` | `workers/.env`, `ZEEBE_GRPC_ADDRESS` |
| Authentication | none sent: the bundled install disables authentication for API access | `workers/.env`, `CAMUNDA_AUTH_STRATEGY=NONE` |
| Worker wiring: the job type each worker serves, how many jobs it activates, its job timeout | one entry per worker | `workers/config/workers.default.json`, key `workers` |
| Simulated external services: outcome, latency and thresholds | one entry per service | `workers/config/workers.default.json`, key `simulatedServices` |
| Business calendar: clinic-letter, pathway-monitoring and escalation periods | one table | `workers/config/workers.default.json`, key `businessCalendar` |
| Forcing one outcome for every job of a worker, which is how a demonstration drives an exception path without editing code | `HPRTAS_SIM_*` | `workers/.env` or the shell |

The worker settings are read in three layers, each overriding the one before it:
`workers/config/workers.default.json` (committed, the agreed defaults), then
`workers/config/workers.local.json` (optional, never committed, for one machine), then environment
variables from `workers/.env` or the shell. `workers/.env` is what `workers/.env.example` is copied
to and is ignored by git, so a machine configures its own connection without changing the committed
defaults. `workers/README.md` describes every setting and what each simulated service can be told to
do.

Deployment is also done programmatically by `python tools/verify_hprtas_engine_forms.py`, which
deploys each model with its forms and then proves it by starting the model and checking that the user
task it reaches resolves a form that was deployed. `models/README.md` under *Deploying a model* gives
the single-model command and the rules a model has to follow to be deployable at all.

## Testing it

The workers carry their own tests and they do not all need an engine:

```bash
cd workers
mvn test                                          # the worker unit tests, no engine
mvn compile exec:java -Dexec.args="--check"               # the configuration and the worker wiring, no engine
mvn test -Pengine -Dtest=SmokeTest                # the workers against a purpose-built fixture, engine running
mvn test -Pengine -Dtest=OperationalModelsTest    # the five operational models and the forms, engine running
```

The engine tests are left out of the default build because they need Camunda 8 Run to be up and they
change what is deployed in it. They skip themselves, rather than fail, when the engine is not
answering.

`workers/README.md` describes the job types, the variables each worker reads and writes, the error
codes and the simulated services. `tests/README.md` describes the test plan and where the evidence
of each run is kept.

The models, the forms and the workers are also checked against each other without an engine, which
is the check to run before committing a model or a form:

```bash
python tools/verify_hprtas_bpmn_bindings.py      # the three artefact types agree
python tools/verify_hprtas_bpmn_layout.py        # the diagrams hold the layout and BPMN rules
python tools/verify_hprtas_engine_forms.py       # the bindings, proved on the engine instead
```

`tools/README.md` lists every script, what it checks and what it needs.
