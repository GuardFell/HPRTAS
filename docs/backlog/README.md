# Backlog

The work to be done, from the whole product down to the sprint.

`product-backlog.md` is the full prioritised list of work, one item per entry using the `PB-###`
identifier.

`task-breakdown.md` breaks each backlog item into tasks small enough for a single sprint, using the
`TB-###` identifier.

`sprint-backlogs.md` records the sprint goals and the tasks selected into each sprint, with the
status the repository's evidence supports and the differences from the plan document left visible
rather than resolved.

`dependencies.md` records what has to come first, what depends on what, and what to do if an item
that others depend on slips.

Identifiers are stable once assigned and are used in commit messages, so a commit names the item it
belongs to: `PB-006 add rejected referral path (TB-010)`. Items are split until each one can be
completed and demonstrated within a single sprint; an item such as "do testing" is too broad to be
useful and has to be broken down. An item's status is one of `Not started`, `In progress`,
`Blocked`, `In review`, `Done` or `Dropped`.
