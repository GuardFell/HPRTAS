# Strategic Models

The high-level view of the business process: the organisational roles that take part, the
responsibilities they carry, and the interactions with systems and organisations outside the
hospital. They exist to show how the work fits together across the organisation before it is
narrowed down into the processes Camunda runs.

`referral-to-treatment-pathway.bpmn` follows the pathway from a referral arriving to treatment
being delivered, at the level of roles and their responsibilities rather than individual tasks.

`patient-pathway-all-entities.bpmn` is the wider view of the same pathway, covering the entities
that take part along the whole of it.

These models are views for analysis. They contain no service tasks and are not executable, so they
are not deployed; Camunda rejects a deployment that contains no executable process. The processes
that are deployed are in `../operational/`, and the strategic activities connect to them there.

The abstraction level is deliberately coarse: a single strategic activity usually corresponds to a
group of tasks in an operational process, and a strategic decision corresponds to a gateway. Where a
strategic activity has no operational counterpart yet, that gap is stated in the model rather than
left to be inferred.
