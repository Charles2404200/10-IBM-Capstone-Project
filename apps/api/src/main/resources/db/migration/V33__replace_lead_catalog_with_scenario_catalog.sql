-- Replace the V32 learner catalogue without deleting any data that an existing
-- engagement could reference. The previous 20 catalogue scenarios are archived;
-- their leads, evidence and completed attempts remain historically valid.
UPDATE scenarios
SET status = 'ARCHIVED', updated_at = NOW(), version = version + 1
WHERE id::text LIKE '60000000-0000-0000-0000-%'
  AND status = 'ACTIVE';

-- A generated seed is deliberately set-based: it is repeatable, fast on a
-- production-sized database, and gives every scenario its own client/persona
-- and 3-4 independently selectable lead variants.
CREATE TEMP TABLE v33_scenario_seed ON COMMIT DROP AS
WITH verticals AS (
    SELECT * FROM (VALUES
        (1, 'AeroVector Aviation', 'Aerospace & Aviation', 'fleet availability', 'predictive maintenance control', 'MRO, telemetry and engineering records', 'airworthiness readiness', 'Elena Vargas', 'VP Asset Operations', 'dispatch reliability is under executive review'),
        (2, 'HarbourGrid Utilities', 'Energy & Utilities', 'field response coordination', 'outage response orchestration', 'GIS, work management and mobile workforce tools', 'network resilience programme', 'Malik Okafor', 'Director of Grid Operations', 'reliability targets are tightening'),
        (3, 'CivicLink Services', 'Public Sector', 'citizen case management', 'service request triage', 'legacy CRM and workflow services', 'ministerial service review', 'Grace Liu', 'Chief Service Officer', 'service standards review announced'),
        (4, 'GreenSpan Developments', 'Real Estate & Construction', 'capital programme controls', 'portfolio delivery assurance', 'cost, schedule and contractor reporting', 'major project gateway review', 'Owen Hart', 'Programme Delivery Director', 'milestone slippage affects funding release'),
        (5, 'Wavefront Media Group', 'Media & Entertainment', 'content operations', 'audience data activation', 'rights, content supply chain and analytics platforms', 'streaming platform relaunch', 'Sofia Bennett', 'Chief Digital Officer', 'subscription retention is under pressure'),
        (6, 'Horizon Hotels Collective', 'Hospitality & Travel', 'guest operations', 'property service consistency', 'PMS, service desk and guest analytics', 'brand experience initiative', 'Noah Patel', 'VP Guest Operations', 'guest satisfaction varies across properties'),
        (7, 'NexaLearn Institute', 'Education', 'student support operations', 'enrolment journey redesign', 'student information and learning systems', 'retention improvement programme', 'Aisha Robinson', 'Chief Student Experience Officer', 'first-year retention target was missed'),
        (8, 'BlueCurrent Water', 'Water & Environment', 'asset inspection planning', 'leak response visibility', 'sensor, asset and field service platforms', 'climate resilience investment', 'Theo Martin', 'Head of Asset Strategy', 'non-revenue water is rising'),
        (9, 'Mosaic Foods Cooperative', 'Food & Agriculture', 'cold-chain traceability', 'supplier quality coordination', 'ERP, warehouse and traceability systems', 'export compliance deadline', 'Camila Torres', 'Director of Supply Chain', 'traceability evidence is required for export'),
        (10, 'LumaCare Clinics', 'Healthcare', 'patient access operations', 'care-capacity optimisation', 'EHR, scheduling and referral systems', 'care access improvement plan', 'Ravi Shah', 'Chief Operating Officer', 'patient wait times are increasing'),
        (11, 'Ironwood Manufacturing', 'Industrial Manufacturing', 'production exception management', 'quality and downtime reduction', 'MES, ERP and machine data', 'plant performance programme', 'Mina Kaur', 'VP Manufacturing Excellence', 'unplanned downtime is above target'),
        (12, 'Verdant Retail Bank', 'Financial Services', 'customer onboarding controls', 'KYC workflow improvement', 'core banking and digital channel services', 'risk remediation plan', 'Daniel Kim', 'Chief Risk Operations Officer', 'remediation milestones are board tracked'),
        (13, 'Pathfinder Mobility', 'Transport & Logistics', 'network capacity planning', 'last-mile delivery control', 'telematics, route and warehouse platforms', 'peak season readiness', 'Jade Wilson', 'VP Network Operations', 'service reliability affects contract renewals'),
        (14, 'Keystone Legal Services', 'Professional Services', 'matter intake operations', 'knowledge and staffing optimisation', 'practice management and knowledge systems', 'client service transformation', 'Henry Brooks', 'Chief Practice Officer', 'utilisation and response time vary by team'),
        (15, 'Northstar Telecom', 'Telecommunications', 'network rollout coordination', 'incident triage modernisation', 'OSS, workforce and network assurance tools', '5G rollout assurance', 'Priyanka Rao', 'Director of Network Delivery', 'rollout dates are commercially committed'),
        (16, 'Solaris Life Sciences', 'Life Sciences', 'clinical supply planning', 'trial site visibility', 'quality, clinical and supply platforms', 'trial acceleration portfolio', 'Lucas Meyer', 'VP Clinical Operations', 'protocol timelines are at risk'),
        (17, 'Granite Insurance', 'Insurance', 'claims operations', 'straight-through claims processing', 'policy, claims and document systems', 'claims transformation programme', 'Natalie Ford', 'Chief Claims Officer', 'cycle time and leakage are under review'),
        (18, 'Cobalt Mining Group', 'Mining & Resources', 'maintenance execution', 'shutdown planning assurance', 'asset, maintenance and production data', 'production stability initiative', 'Ethan Cole', 'General Manager Operations', 'availability loss is reducing output'),
        (19, 'Arbor Social Housing', 'Housing & Community', 'repairs case management', 'tenant service recovery', 'housing management and communications systems', 'service recovery plan', 'Imani Price', 'Director of Customer Services', 'repair completion times are escalating'),
        (20, 'Brightline Consumer Goods', 'Consumer Products', 'demand planning', 'trade promotion execution', 'planning, distributor and CRM data', 'margin protection programme', 'Marco Silva', 'VP Commercial Operations', 'forecast error is eroding margin'),
        (21, 'Momentum Auto Group', 'Automotive', 'dealer service operations', 'connected service experience', 'dealer, warranty and customer data', 'after-sales growth programme', 'Harper Nguyen', 'Director of Customer Experience', 'service retention is declining'),
        (22, 'Meridian Cloudworks', 'Technology', 'enterprise delivery operations', 'customer onboarding acceleration', 'delivery, support and product telemetry', 'enterprise scale-up plan', 'Jon Bell', 'VP Customer Delivery', 'implementation backlog is affecting renewals'),
        (23, 'Coastal State Government', 'Government', 'grant administration', 'casework transparency', 'case management, records and payments platforms', 'public value improvement review', 'Fiona Walsh', 'Deputy Secretary, Service Delivery', 'audit findings require a measurable response'),
        (24, 'CommonGround Foundation', 'Nonprofit & Social Impact', 'programme impact reporting', 'funding outcome visibility', 'CRM, grants and impact measurement tools', 'funding renewal cycle', 'Samuel Adeyemi', 'Chief Programmes Officer', 'funders need clearer impact evidence')
    ) AS vertical(number, company_prefix, industry, operating_area, opportunity, technology, trigger, persona_name, persona_title, pain_signal)
), regions AS (
    SELECT ARRAY['Pacific', 'Northern', 'Urban', 'Coastal', 'Summit', 'Cedar', 'Atlas', 'Meridian', 'Pioneer', 'Aurora'] AS names
)
SELECT vertical.*, sequence.n AS scenario_number,
       regions.names[((sequence.n - 1) % cardinality(regions.names)) + 1] AS region
FROM generate_series(1, 2000) AS sequence(n)
JOIN verticals vertical ON vertical.number = ((sequence.n - 1) % 24) + 1
CROSS JOIN regions;

CREATE TEMP TABLE v33_lead_seed ON COMMIT DROP AS
SELECT scenario_seed.*, lead_number,
       row_number() OVER (ORDER BY scenario_number, lead_number) AS lead_sequence
FROM v33_scenario_seed scenario_seed
CROSS JOIN LATERAL generate_series(1, CASE WHEN scenario_number % 3 = 0 THEN 4 ELSE 3 END) AS lead_number;

INSERT INTO scenarios (
    id, title, industry, description, status, difficulty, content_version,
    scenario_lineage_id, information_ambiguity, stakeholder_complexity,
    commercial_pressure, consultant_role, objective, success_criteria,
    simulated_days, created_at, updated_at, version
)
SELECT
    ('62000000-0000-0000-0000-' || lpad(scenario_number::text, 12, '0'))::uuid,
    company_prefix || ' ' || region || ' ' || lpad(scenario_number::text, 4, '0') || ': ' || opportunity,
    industry,
    'A ' || industry || ' consulting scenario where the learner must improve ' || operating_area ||
        ' through ' || opportunity || ' while responding to the client pressure that ' || pain_signal || '.',
    'ACTIVE',
    CASE WHEN scenario_number % 5 IN (1, 2) THEN 2 WHEN scenario_number % 5 IN (3, 4) THEN 3 ELSE 4 END,
    1,
    ('62000000-0000-0000-0000-' || lpad(scenario_number::text, 12, '0'))::uuid,
    CASE WHEN scenario_number % 5 IN (1, 2) THEN 2 WHEN scenario_number % 5 = 3 THEN 3 ELSE 4 END,
    CASE WHEN scenario_number % 4 = 0 THEN 4 ELSE 3 END,
    CASE WHEN scenario_number % 3 = 0 THEN 4 ELSE 3 END,
    'Enterprise Transformation Consultant',
    'Discover the operating constraints around ' || operating_area || ', build a grounded hypothesis and earn agreement on a low-risk next step.',
    'Identify the operational problem|Validate stakeholder priorities|Quantify a credible impact|Secure agreement on a measured pilot',
    CASE WHEN scenario_number % 3 = 0 THEN 14 ELSE 10 END,
    NOW(), NOW(), 0
FROM v33_scenario_seed
ON CONFLICT (id) DO NOTHING;

INSERT INTO personas (
    id, scenario_id, name, job_title, organisation, communication_style,
    visible_concerns, hidden_concerns, business_goals, prompt_version,
    created_at, updated_at, version
)
SELECT
    ('63000000-0000-0000-0000-' || lpad(scenario_number::text, 12, '0'))::uuid,
    ('62000000-0000-0000-0000-' || lpad(scenario_number::text, 12, '0'))::uuid,
    persona_name || ' ' || region, persona_title,
    company_prefix || ' ' || region || ' ' || lpad(scenario_number::text, 4, '0'),
    'Direct, thoughtful and time-constrained. Appreciates precise discovery, challenges unsupported assumptions, and protects operational teams from unnecessary disruption.',
    pain_signal || '; wants a credible decision around ' || opportunity || '.',
    'Needs a defensible plan that preserves service continuity and gives leadership observable evidence before wider investment.',
    'Create an evidence-backed, low-risk route to improve ' || operating_area || ' with a measurable business outcome.',
    1, NOW(), NOW(), 0
FROM v33_scenario_seed
ON CONFLICT (id) DO NOTHING;

INSERT INTO leads (
    id, scenario_id, company_name, industry, public_description, difficulty,
    potential_value_range, decision_maker, technology_stack, budget_signal,
    pain_severity, created_at, updated_at, version
)
SELECT
    ('64000000-0000-0000-0000-' || lpad(lead_sequence::text, 12, '0'))::uuid,
    ('62000000-0000-0000-0000-' || lpad(scenario_number::text, 12, '0'))::uuid,
    company_prefix || ' ' || region || ' ' ||
        (ARRAY['Operations', 'Services', 'Networks', 'Partners'])[lead_number] || ' Opportunity',
    industry,
    'A ' || industry || ' organisation evaluating ' || opportunity || ' to improve ' || operating_area || ' without disrupting critical delivery.',
    CASE WHEN (scenario_number + lead_number) % 5 IN (1, 2) THEN 'EASY'
         WHEN (scenario_number + lead_number) % 5 IN (3, 4) THEN 'MEDIUM' ELSE 'HARD' END,
    CASE WHEN lead_number = 1 THEN '$750K - $1.8M' WHEN lead_number = 2 THEN '$1.2M - $3M' ELSE '$2M - $5M' END,
    CASE WHEN lead_number = 4 THEN 'Unconfirmed sponsor - stakeholder mapping required'
         ELSE persona_name || ' ' || region || ', ' || persona_title END,
    technology || ' with local reporting workarounds',
    CASE WHEN lead_number = 4 THEN 'No confirmed budget; validate commercial appetite'
         ELSE 'Funding is being evaluated alongside ' || trigger END,
    CASE WHEN (scenario_number + lead_number) % 4 = 0 THEN 'High - ' || pain_signal ELSE 'Medium - ' || pain_signal END,
    NOW(), NOW(), 0
FROM v33_lead_seed
ON CONFLICT (id) DO NOTHING;

INSERT INTO lead_signals (id, lead_id, label, category)
SELECT
    ('65000000-0000-0000-0000-' || lpad(((lead_sequence - 1) * 2 + signal_number)::text, 12, '0'))::uuid,
    ('64000000-0000-0000-0000-' || lpad(lead_sequence::text, 12, '0'))::uuid,
    CASE WHEN signal_number = 1 THEN trigger ELSE pain_signal END,
    CASE WHEN signal_number = 1 THEN 'BUSINESS_TRIGGER' ELSE 'OPERATIONAL' END
FROM v33_lead_seed
CROSS JOIN generate_series(1, 2) AS signal_number
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_scenarios_active_catalog
    ON scenarios (industry, difficulty, title) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_scenarios_active_title_lower
    ON scenarios (lower(title)) WHERE status = 'ACTIVE';

ANALYZE scenarios;
ANALYZE leads;
