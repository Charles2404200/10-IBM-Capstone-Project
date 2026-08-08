-- Phase 4: broaden beyond the single healthcare scenario/persona so the platform
-- offers multiple stakeholder personalities and industries (§Phase 4 - Realism & Expansion).

-- Second persona on the existing MediCare scenario: a skeptical, budget-focused CFO
-- who reacts very differently to the same discovery/outreach/meeting content than
-- the original CIO persona, forcing the learner to adapt their approach.
INSERT INTO personas (id, scenario_id, name, job_title, organisation, communication_style,
                       visible_concerns, hidden_concerns, business_goals, prompt_version,
                       created_at, updated_at, version)
VALUES (
    'b2c3d4e5-0000-0000-0000-000000000002',
    'a1b2c3d4-0000-0000-0000-000000000001',
    'Marcus Ibrahim',
    'Chief Financial Officer',
    'MediCare Regional Hospital Network',
    'Skeptical, numbers-first, interrupts vague pitches. Wants payback period and total cost of ownership before anything else. Warms up only when the consultant demonstrates fiscal discipline.',
    'Capital budget already stretched by facility upgrades; board wants proof of ROI within 18 months',
    'Privately worried the CIO oversold the urgency of this project to the board; does not want to be blamed if it fails',
    'Defensible ROI model; phased spend that can be paused if results lag; no surprise cost overruns',
    1,
    NOW(), NOW(), 0
);

-- Second scenario: a retail/logistics engagement, distinct industry and difficulty,
-- with its own persona so learners can practise across varied consulting contexts.
INSERT INTO scenarios (id, title, industry, description, status, difficulty, content_version,
                        created_at, updated_at, version)
VALUES (
    'a1b2c3d4-0000-0000-0000-000000000002',
    'NorthPeak Retail Supply Chain Modernisation',
    'Retail & Logistics',
    'NorthPeak Retail Group operates 60 stores and a regional distribution centre. Repeated stockouts and manual inventory reconciliation are costing an estimated $2M/year in lost sales. Leadership is split on whether to invest in a new platform or patch the existing systems.',
    'ACTIVE',
    3,
    1,
    NOW(), NOW(), 0
);

INSERT INTO personas (id, scenario_id, name, job_title, organisation, communication_style,
                       visible_concerns, hidden_concerns, business_goals, prompt_version,
                       created_at, updated_at, version)
VALUES (
    'b2c3d4e5-0000-0000-0000-000000000003',
    'a1b2c3d4-0000-0000-0000-000000000002',
    'Priya Nathan',
    'VP of Supply Chain Operations',
    'NorthPeak Retail Group',
    'Energetic, impatient, prefers concrete pilots over long studies. Pushes back hard on anything that sounds like a multi-year transformation. Values consultants who have shopfloor credibility.',
    'Persistent stockouts during peak season; distribution centre running on spreadsheets; competitor undercutting on delivery speed',
    'Fears being replaced if she cannot show a quick win within the next quarter; board is quietly benchmarking her against a competitor VP',
    'A working pilot within one quarter; inventory accuracy above 98%; measurable reduction in stockout-driven lost sales',
    1,
    NOW(), NOW(), 0
);

-- A second persona variant for NorthPeak: a cautious store-operations director who
-- must be won over separately from the VP, giving learners a distinct personality
-- to practise against within the same industry/scenario.
INSERT INTO personas (id, scenario_id, name, job_title, organisation, communication_style,
                       visible_concerns, hidden_concerns, business_goals, prompt_version,
                       created_at, updated_at, version)
VALUES (
    'b2c3d4e5-0000-0000-0000-000000000004',
    'a1b2c3d4-0000-0000-0000-000000000002',
    'Daniel Osei',
    'Director of Store Operations',
    'NorthPeak Retail Group',
    'Cautious, process-oriented, worried about disruption to daily store operations during rollout. Prefers detailed change-management plans over ambitious timelines.',
    'Store staff already stretched thin; past system rollout caused a week of checkout outages',
    'Was blamed for the last rollout failure even though it was an IT issue; extremely risk-averse as a result',
    'Zero disruption to daily store operations; thorough staff training before go-live; a rollback plan for every phase',
    1,
    NOW(), NOW(), 0
);

-- Leads for the new scenario, mirroring the easy/hard pattern established for MediCare.
INSERT INTO leads (id, scenario_id, company_name, industry, public_description, difficulty,
                    created_at, updated_at, version)
VALUES (
    'c3d4e5f6-0000-0000-0000-000000000003',
    'a1b2c3d4-0000-0000-0000-000000000002',
    'NorthPeak Retail Group',
    'Retail',
    'A 60-store retail chain with a regional distribution centre facing recurring stockouts and manual inventory processes.',
    'MEDIUM',
    NOW(), NOW(), 0
);

INSERT INTO lead_signals (id, lead_id, label, category) VALUES
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000003', 'Stockouts reported in 3 consecutive quarters', 'OPERATIONAL'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000003', 'New VP of Supply Chain hired 6 months ago', 'LEADERSHIP_CHANGE'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000003', 'Board reviewing technology budget next quarter', 'FINANCIAL_SIGNAL');

INSERT INTO leads (id, scenario_id, company_name, industry, public_description, difficulty,
                    created_at, updated_at, version)
VALUES (
    'c3d4e5f6-0000-0000-0000-000000000004',
    'a1b2c3d4-0000-0000-0000-000000000002',
    'NorthPeak Distribution Partners',
    'Logistics',
    'A third-party logistics partner to NorthPeak, exploring whether to modernise jointly or go it alone. Limited public information available.',
    'HARD',
    NOW(), NOW(), 0
);

INSERT INTO lead_signals (id, lead_id, label, category) VALUES
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000004', 'Contract renewal with NorthPeak due in 9 months', 'FINANCIAL_SIGNAL'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000004', 'No dedicated technology leadership role', 'RISK_INDICATOR');
