-- Phase 3 follow-up: "enterprise showcase" depth pass.
--  1. Scenario briefing content + multi-dimensional difficulty (Command Centre
--     cockpit + pre-engagement briefing modal).
--  2. Lead hidden-intelligence fields that are revealed progressively as the
--     learner collects research evidence (Client Intelligence "Client Profile"
--     panel), instead of every fact being visible on the pipeline card.

ALTER TABLE scenarios
    ADD COLUMN information_ambiguity   INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN stakeholder_complexity  INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN commercial_pressure     INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN consultant_role         VARCHAR(150) NOT NULL DEFAULT 'Management Consultant',
    ADD COLUMN objective               TEXT NOT NULL DEFAULT '',
    ADD COLUMN success_criteria        TEXT NOT NULL DEFAULT '',
    ADD COLUMN simulated_days          INTEGER NOT NULL DEFAULT 10;

ALTER TABLE leads
    ADD COLUMN potential_value_range VARCHAR(100),
    ADD COLUMN decision_maker        VARCHAR(150),
    ADD COLUMN technology_stack      VARCHAR(200),
    ADD COLUMN budget_signal         VARCHAR(150),
    ADD COLUMN pain_severity         VARCHAR(100);

-- Backfill briefing content + difficulty dimensions for the seeded scenarios.
UPDATE scenarios SET
    information_ambiguity = 2, stakeholder_complexity = 2, commercial_pressure = 3,
    consultant_role = 'Technology Strategy Consultant',
    objective = 'Identify a credible transformation opportunity within MediCare Regional Hospital Network and secure sponsorship for a data-integration initiative ahead of the regulatory audit.',
    success_criteria = 'Identify the core data-integration problem|Build stakeholder trust with the CIO|Develop an evidence-backed recommendation|Secure proposal approval',
    simulated_days = 10
WHERE id = 'a1b2c3d4-0000-0000-0000-000000000001';

UPDATE scenarios SET
    information_ambiguity = 4, stakeholder_complexity = 3, commercial_pressure = 4,
    consultant_role = 'Supply Chain Technology Consultant',
    objective = 'Diagnose the root cause of recurring stockouts at NorthPeak Retail Group and secure sponsorship for a modernisation pilot before the next board review.',
    success_criteria = 'Identify the core operational problem|Build stakeholder trust across VP and store operations|Develop an evidence-backed recommendation|Secure proposal approval',
    simulated_days = 12
WHERE id = 'a1b2c3d4-0000-0000-0000-000000000002';

-- Backfill hidden intelligence for the seeded leads (revealed progressively based
-- on evidence collected — see LeadIntelligencePolicy).
UPDATE leads SET
    potential_value_range = '$1.5M – $3M', decision_maker = 'Sarah Chen, Chief Information Officer',
    technology_stack = 'Legacy on-prem EHR + 4 disconnected hospital data systems',
    budget_signal = 'Board-approved IT modernisation budget, contingent on audit outcome',
    pain_severity = 'High — regulatory deadline in 90 days'
WHERE id = 'c3d4e5f6-0000-0000-0000-000000000001';

UPDATE leads SET
    potential_value_range = '$400K – $900K', decision_maker = 'Unknown — no confirmed IT sponsor',
    technology_stack = 'Unknown', budget_signal = 'No confirmed budget line',
    pain_severity = 'Medium — supply chain visibility gap, no urgent deadline'
WHERE id = 'c3d4e5f6-0000-0000-0000-000000000002';

UPDATE leads SET
    potential_value_range = '$2M – $4M', decision_maker = 'Priya Nathan, VP of Supply Chain Operations',
    technology_stack = 'SAP ERP + legacy warehouse management system (WMS)',
    budget_signal = 'Board reviewing technology budget next quarter',
    pain_severity = 'High — stockouts reported 3 consecutive quarters'
WHERE id = 'c3d4e5f6-0000-0000-0000-000000000003';

UPDATE leads SET
    potential_value_range = '$500K – $1.2M', decision_maker = 'Unknown — no dedicated technology leadership role',
    technology_stack = 'Unknown', budget_signal = 'Contract renewal budget only, no discretionary spend confirmed',
    pain_severity = 'Medium — contract renewal pressure, limited public information'
WHERE id = 'c3d4e5f6-0000-0000-0000-000000000004';
