# Hospital Patient Referral, Treatment and Administration System (HPRTAS)

HPRTAS is a hospital patient administration system covering the pathway from a referral arriving
from a general practitioner or another hospital, through the clinical decision, the funding and
payment of treatment, the treatment bookings themselves, the clinic letters that follow, and the
follow-up, cancellation, enquiry and refund handling that comes after. It is built as a set of
executable BPMN processes on Camunda 8, with external workers carrying out the automated steps
that talk to systems outside the hospital.

The repository holds the models, the forms the staff use, the workers, the documentation and the
test evidence for that system.

## What is in the repository

`models/` holds the BPMN. `models/strategic/` contains the high-level view of the wider process,
`models/socio-technical/` the i\* Strategic Dependency and Strategic Rationale models, and
`models/operational/` the four executable processes that Camunda runs.

`forms/` holds the Camunda Forms the user tasks are bound to, one `.form` file per form, each with
the form key the model refers to.

`workers/` holds the external workers as a Maven project: the job workers themselves, the
simulated external services they call, and their configuration.

`docs/` holds everything that describes the project rather than implements it: the case study
summary, the requirements and their traceability, and the backlog, planning and working agreements
that go with delivering it.

`tests/` holds the test plan and the evidence of the runs.

`Enterprise Architecture/` holds the portfolio deliverable describing the enterprise and its
information systems, and the Zachman Framework view of them.

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
them, so no task is left pointing at a form key nothing resolves. From the workspace root,
`python tools\verify_hprtas_engine_forms.py` does the same thing and then proves it: it deploys each
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
started by sending that message rather than from the Processes page.
`simple-clinic-letter-lanes` is the second, lane-level view of the clinic letter process: it is
deployable and runnable in the same way, and its steps are the same steps `core-3` carries.

The strategic model is a non-executable view of the process. Do not deploy it: Camunda rejects a
deployment that contains no executable process.

## Testing it

The workers carry their own tests and they do not all need an engine:

```bash
cd workers
mvn test                                          # the worker unit tests, no engine
mvn compile exec:java -Dexec.args="--check"               # the configuration and the worker wiring, no engine
mvn test -Pengine -Dtest=SmokeTest                # the workers against a purpose-built fixture, engine running
mvn test -Pengine -Dtest=OperationalModelsTest    # the four operational models and the forms, engine running
```

The engine tests are left out of the default build because they need Camunda 8 Run to be up and they
change what is deployed in it. They skip themselves, rather than fail, when the engine is not
answering.

`workers/README.md` describes the job types, the variables each worker reads and writes, the error
codes and the simulated services. `tests/README.md` describes the test plan and where the evidence
of each run is kept.
