# AI evaluation regression corpus

This corpus is a versioned set of behaviour contracts for the consulting simulation. It runs against production domain policies in the normal Gradle test suite and therefore does not call a live provider or depend on quota, latency, or provider availability.

Each fixture represents an auditable learner transcript or model contract. Add a fixture before changing a prompt, parser, scoring policy, provider route, or proposal/research guard. A behavioural change is accepted only when the expected outcome is deliberately updated and reviewed.

Coverage:

- Research: quality over volume, evidence unlocks intelligence, and unsupported fact IDs are rejected.
- Outreach: noise cannot progress, abusive language is rejected, and grounded outreach can progress.
- Live meeting: greetings are neutral, unprepared responses lose credit, professionalism is mandatory, and natural closure requires both relationship readiness and a client commitment.
- Proposal: required evidence links and source validity remain deterministic.

Run locally with `./gradlew.bat test --tests "*AiEvaluationRegressionSuiteTest"` from `apps/api`.
