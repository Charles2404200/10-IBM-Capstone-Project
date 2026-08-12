-- One-time enterprise catalogue expansion. Flyway runs this exactly once per
-- database; it never mutates a learner's existing scenario or engagement.
-- Each vertical receives its own scenario/persona so a lead's industry,
-- stakeholder and canonical workflow remain coherent all the way to outcome.

WITH catalogue AS (
    SELECT * FROM (VALUES
        (1, 'AeroFleet Systems', 'Aerospace & Aviation', 'fleet maintenance planning', 'predictive maintenance', 'MRO scheduling', 'airworthiness audit', 'Elena Vargas', 'VP Asset Operations', 'dispatch reliability is under executive review'),
        (2, 'HarbourGrid Utilities', 'Energy & Utilities', 'field service dispatch', 'outage response coordination', 'GIS and work management integration', 'network resilience programme', 'Malik Okafor', 'Director of Grid Operations', 'reliability targets are tightening'),
        (3, 'CivicLink Services', 'Public Sector', 'citizen case management', 'service request triage', 'legacy CRM consolidation', 'ministerial service review', 'Grace Liu', 'Chief Service Officer', 'service standards review announced'),
        (4, 'GreenSpan Developments', 'Real Estate & Construction', 'capital project controls', 'portfolio delivery assurance', 'cost and schedule reporting', 'major project gateway review', 'Owen Hart', 'Programme Delivery Director', 'milestone slippage affects funding release'),
        (5, 'Wavefront Media Group', 'Media & Entertainment', 'rights and content workflow', 'audience data activation', 'content supply chain orchestration', 'streaming platform relaunch', 'Sofia Bennett', 'Chief Digital Officer', 'subscription retention is under pressure'),
        (6, 'Horizon Hotels Collective', 'Hospitality & Travel', 'guest operations planning', 'property service consistency', 'PMS integration and analytics', 'brand experience initiative', 'Noah Patel', 'VP Guest Operations', 'guest satisfaction varies across properties'),
        (7, 'NexaLearn Institute', 'Education', 'student support operations', 'enrolment journey redesign', 'student information system integration', 'retention improvement programme', 'Aisha Robinson', 'Chief Student Experience Officer', 'first-year retention target was missed'),
        (8, 'BlueCurrent Water', 'Water & Environment', 'asset inspection planning', 'leak response visibility', 'mobile workforce and sensor integration', 'climate resilience investment', 'Theo Martin', 'Head of Asset Strategy', 'non-revenue water is rising'),
        (9, 'Mosaic Foods Cooperative', 'Food & Agriculture', 'cold chain traceability', 'supplier quality coordination', 'ERP and warehouse visibility', 'export compliance deadline', 'Camila Torres', 'Director of Supply Chain', 'traceability evidence is required for export'),
        (10, 'LumaCare Clinics', 'Health & Wellness', 'patient access operations', 'appointment capacity management', 'EHR and scheduling interoperability', 'care access improvement plan', 'Ravi Shah', 'Chief Operating Officer', 'patient wait times are increasing'),
        (11, 'Ironwood Manufacturing', 'Industrial Manufacturing', 'production exception management', 'quality and downtime reduction', 'MES and ERP data integration', 'plant performance programme', 'Mina Kaur', 'VP Manufacturing Excellence', 'unplanned downtime is above target'),
        (12, 'Verdant Retail Bank', 'Financial Services', 'customer onboarding controls', 'KYC workflow improvement', 'core banking and digital channel integration', 'risk remediation plan', 'Daniel Kim', 'Chief Risk Operations Officer', 'remediation milestones are board tracked'),
        (13, 'Pathfinder Mobility', 'Transport & Logistics', 'network capacity planning', 'last-mile delivery control', 'telematics and route platform integration', 'peak season readiness', 'Jade Wilson', 'VP Network Operations', 'service reliability affects contract renewals'),
        (14, 'Keystone Legal Services', 'Professional Services', 'matter intake operations', 'knowledge reuse and staffing', 'practice management data quality', 'client service transformation', 'Henry Brooks', 'Chief Practice Officer', 'utilisation and response time vary by team'),
        (15, 'Northstar Telecom', 'Telecommunications', 'field rollout coordination', 'network incident triage', 'OSS and workforce management integration', '5G rollout assurance', 'Priyanka Rao', 'Director of Network Delivery', 'rollout dates are commercially committed'),
        (16, 'Solaris Life Sciences', 'Life Sciences', 'clinical supply planning', 'trial site visibility', 'quality system interoperability', 'trial acceleration portfolio', 'Lucas Meyer', 'VP Clinical Operations', 'protocol timelines are at risk'),
        (17, 'Granite Insurance', 'Insurance', 'claims operations', 'straight-through processing', 'policy and claims platform integration', 'claims transformation programme', 'Natalie Ford', 'Chief Claims Officer', 'cycle time and leakage are under review'),
        (18, 'Cobalt Mining Group', 'Mining & Resources', 'maintenance execution', 'shutdown planning', 'asset data governance', 'production stability initiative', 'Ethan Cole', 'General Manager Operations', 'availability loss is reducing output'),
        (19, 'Arbor Social Housing', 'Housing & Community', 'repairs case management', 'tenant communications', 'housing management system integration', 'service recovery plan', 'Imani Price', 'Director of Customer Services', 'repair completion times are escalating'),
        (20, 'Brightline Consumer Goods', 'Consumer Products', 'demand planning', 'trade promotion execution', 'planning and distributor data integration', 'margin protection programme', 'Marco Silva', 'VP Commercial Operations', 'forecast error is eroding margin')
    ) AS vertical(number, company_prefix, industry, operating_area, opportunity, technology, trigger, persona_name, persona_title, pain_signal)
)
INSERT INTO scenarios (id, title, industry, description, status, difficulty, content_version, scenario_lineage_id, created_at, updated_at, version)
SELECT
    ('60000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
    company_prefix || ' ' || opportunity || ' programme',
    industry,
    'A controlled ' || industry || ' consulting scenario focused on ' || operating_area || ', with a decision to make around ' || opportunity || '.',
    'ACTIVE',
    CASE WHEN number % 3 = 1 THEN 2 WHEN number % 3 = 2 THEN 3 ELSE 4 END,
    1,
    ('60000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
    NOW(), NOW(), 0
FROM catalogue
ON CONFLICT (id) DO NOTHING;

WITH catalogue AS (
    SELECT * FROM (VALUES
        (1, 'AeroFleet Systems', 'Aerospace & Aviation', 'fleet maintenance planning', 'predictive maintenance', 'MRO scheduling', 'airworthiness audit', 'Elena Vargas', 'VP Asset Operations', 'dispatch reliability is under executive review'),
        (2, 'HarbourGrid Utilities', 'Energy & Utilities', 'field service dispatch', 'outage response coordination', 'GIS and work management integration', 'network resilience programme', 'Malik Okafor', 'Director of Grid Operations', 'reliability targets are tightening'),
        (3, 'CivicLink Services', 'Public Sector', 'citizen case management', 'service request triage', 'legacy CRM consolidation', 'ministerial service review', 'Grace Liu', 'Chief Service Officer', 'service standards review announced'),
        (4, 'GreenSpan Developments', 'Real Estate & Construction', 'capital project controls', 'portfolio delivery assurance', 'cost and schedule reporting', 'major project gateway review', 'Owen Hart', 'Programme Delivery Director', 'milestone slippage affects funding release'),
        (5, 'Wavefront Media Group', 'Media & Entertainment', 'rights and content workflow', 'audience data activation', 'content supply chain orchestration', 'streaming platform relaunch', 'Sofia Bennett', 'Chief Digital Officer', 'subscription retention is under pressure'),
        (6, 'Horizon Hotels Collective', 'Hospitality & Travel', 'guest operations planning', 'property service consistency', 'PMS integration and analytics', 'brand experience initiative', 'Noah Patel', 'VP Guest Operations', 'guest satisfaction varies across properties'),
        (7, 'NexaLearn Institute', 'Education', 'student support operations', 'enrolment journey redesign', 'student information system integration', 'retention improvement programme', 'Aisha Robinson', 'Chief Student Experience Officer', 'first-year retention target was missed'),
        (8, 'BlueCurrent Water', 'Water & Environment', 'asset inspection planning', 'leak response visibility', 'mobile workforce and sensor integration', 'climate resilience investment', 'Theo Martin', 'Head of Asset Strategy', 'non-revenue water is rising'),
        (9, 'Mosaic Foods Cooperative', 'Food & Agriculture', 'cold chain traceability', 'supplier quality coordination', 'ERP and warehouse visibility', 'export compliance deadline', 'Camila Torres', 'Director of Supply Chain', 'traceability evidence is required for export'),
        (10, 'LumaCare Clinics', 'Health & Wellness', 'patient access operations', 'appointment capacity management', 'EHR and scheduling interoperability', 'care access improvement plan', 'Ravi Shah', 'Chief Operating Officer', 'patient wait times are increasing'),
        (11, 'Ironwood Manufacturing', 'Industrial Manufacturing', 'production exception management', 'quality and downtime reduction', 'MES and ERP data integration', 'plant performance programme', 'Mina Kaur', 'VP Manufacturing Excellence', 'unplanned downtime is above target'),
        (12, 'Verdant Retail Bank', 'Financial Services', 'customer onboarding controls', 'KYC workflow improvement', 'core banking and digital channel integration', 'risk remediation plan', 'Daniel Kim', 'Chief Risk Operations Officer', 'remediation milestones are board tracked'),
        (13, 'Pathfinder Mobility', 'Transport & Logistics', 'network capacity planning', 'last-mile delivery control', 'telematics and route platform integration', 'peak season readiness', 'Jade Wilson', 'VP Network Operations', 'service reliability affects contract renewals'),
        (14, 'Keystone Legal Services', 'Professional Services', 'matter intake operations', 'knowledge reuse and staffing', 'practice management data quality', 'client service transformation', 'Henry Brooks', 'Chief Practice Officer', 'utilisation and response time vary by team'),
        (15, 'Northstar Telecom', 'Telecommunications', 'field rollout coordination', 'network incident triage', 'OSS and workforce management integration', '5G rollout assurance', 'Priyanka Rao', 'Director of Network Delivery', 'rollout dates are commercially committed'),
        (16, 'Solaris Life Sciences', 'Life Sciences', 'clinical supply planning', 'trial site visibility', 'quality system interoperability', 'trial acceleration portfolio', 'Lucas Meyer', 'VP Clinical Operations', 'protocol timelines are at risk'),
        (17, 'Granite Insurance', 'Insurance', 'claims operations', 'straight-through processing', 'policy and claims platform integration', 'claims transformation programme', 'Natalie Ford', 'Chief Claims Officer', 'cycle time and leakage are under review'),
        (18, 'Cobalt Mining Group', 'Mining & Resources', 'maintenance execution', 'shutdown planning', 'asset data governance', 'production stability initiative', 'Ethan Cole', 'General Manager Operations', 'availability loss is reducing output'),
        (19, 'Arbor Social Housing', 'Housing & Community', 'repairs case management', 'tenant communications', 'housing management system integration', 'service recovery plan', 'Imani Price', 'Director of Customer Services', 'repair completion times are escalating'),
        (20, 'Brightline Consumer Goods', 'Consumer Products', 'demand planning', 'trade promotion execution', 'planning and distributor data integration', 'margin protection programme', 'Marco Silva', 'VP Commercial Operations', 'forecast error is eroding margin')
    ) AS vertical(number, company_prefix, industry, operating_area, opportunity, technology, trigger, persona_name, persona_title, pain_signal)
)
INSERT INTO personas (id, scenario_id, name, job_title, organisation, communication_style, visible_concerns, hidden_concerns, business_goals, prompt_version, created_at, updated_at, version)
SELECT
    ('61000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
    ('60000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
    persona_name, persona_title, company_prefix,
    'Direct and practical. Challenges vague claims, expects credible evidence, and protects operational teams from unnecessary disruption.',
    pain_signal || '; decision required around ' || opportunity || '; limited tolerance for generic transformation language.',
    'Needs a defensible recommendation that preserves day-to-day delivery and demonstrates measurable value before wider investment.',
    'Create an evidence-backed, low-risk path to improve ' || operating_area || ' and secure agreement on a measured next step.',
    1, NOW(), NOW(), 0
FROM catalogue
ON CONFLICT (id) DO NOTHING;

WITH catalogue AS (
    SELECT * FROM (VALUES
        (1, 'AeroFleet Systems', 'Aerospace & Aviation', 'fleet maintenance planning', 'predictive maintenance', 'MRO scheduling', 'airworthiness audit', 'Elena Vargas', 'VP Asset Operations', 'dispatch reliability is under executive review'),
        (2, 'HarbourGrid Utilities', 'Energy & Utilities', 'field service dispatch', 'outage response coordination', 'GIS and work management integration', 'network resilience programme', 'Malik Okafor', 'Director of Grid Operations', 'reliability targets are tightening'),
        (3, 'CivicLink Services', 'Public Sector', 'citizen case management', 'service request triage', 'legacy CRM consolidation', 'ministerial service review', 'Grace Liu', 'Chief Service Officer', 'service standards review announced'),
        (4, 'GreenSpan Developments', 'Real Estate & Construction', 'capital project controls', 'portfolio delivery assurance', 'cost and schedule reporting', 'major project gateway review', 'Owen Hart', 'Programme Delivery Director', 'milestone slippage affects funding release'),
        (5, 'Wavefront Media Group', 'Media & Entertainment', 'rights and content workflow', 'audience data activation', 'content supply chain orchestration', 'streaming platform relaunch', 'Sofia Bennett', 'Chief Digital Officer', 'subscription retention is under pressure'),
        (6, 'Horizon Hotels Collective', 'Hospitality & Travel', 'guest operations planning', 'property service consistency', 'PMS integration and analytics', 'brand experience initiative', 'Noah Patel', 'VP Guest Operations', 'guest satisfaction varies across properties'),
        (7, 'NexaLearn Institute', 'Education', 'student support operations', 'enrolment journey redesign', 'student information system integration', 'retention improvement programme', 'Aisha Robinson', 'Chief Student Experience Officer', 'first-year retention target was missed'),
        (8, 'BlueCurrent Water', 'Water & Environment', 'asset inspection planning', 'leak response visibility', 'mobile workforce and sensor integration', 'climate resilience investment', 'Theo Martin', 'Head of Asset Strategy', 'non-revenue water is rising'),
        (9, 'Mosaic Foods Cooperative', 'Food & Agriculture', 'cold chain traceability', 'supplier quality coordination', 'ERP and warehouse visibility', 'export compliance deadline', 'Camila Torres', 'Director of Supply Chain', 'traceability evidence is required for export'),
        (10, 'LumaCare Clinics', 'Health & Wellness', 'patient access operations', 'appointment capacity management', 'EHR and scheduling interoperability', 'care access improvement plan', 'Ravi Shah', 'Chief Operating Officer', 'patient wait times are increasing'),
        (11, 'Ironwood Manufacturing', 'Industrial Manufacturing', 'production exception management', 'quality and downtime reduction', 'MES and ERP data integration', 'plant performance programme', 'Mina Kaur', 'VP Manufacturing Excellence', 'unplanned downtime is above target'),
        (12, 'Verdant Retail Bank', 'Financial Services', 'customer onboarding controls', 'KYC workflow improvement', 'core banking and digital channel integration', 'risk remediation plan', 'Daniel Kim', 'Chief Risk Operations Officer', 'remediation milestones are board tracked'),
        (13, 'Pathfinder Mobility', 'Transport & Logistics', 'network capacity planning', 'last-mile delivery control', 'telematics and route platform integration', 'peak season readiness', 'Jade Wilson', 'VP Network Operations', 'service reliability affects contract renewals'),
        (14, 'Keystone Legal Services', 'Professional Services', 'matter intake operations', 'knowledge reuse and staffing', 'practice management data quality', 'client service transformation', 'Henry Brooks', 'Chief Practice Officer', 'utilisation and response time vary by team'),
        (15, 'Northstar Telecom', 'Telecommunications', 'field rollout coordination', 'network incident triage', 'OSS and workforce management integration', '5G rollout assurance', 'Priyanka Rao', 'Director of Network Delivery', 'rollout dates are commercially committed'),
        (16, 'Solaris Life Sciences', 'Life Sciences', 'clinical supply planning', 'trial site visibility', 'quality system interoperability', 'trial acceleration portfolio', 'Lucas Meyer', 'VP Clinical Operations', 'protocol timelines are at risk'),
        (17, 'Granite Insurance', 'Insurance', 'claims operations', 'straight-through processing', 'policy and claims platform integration', 'claims transformation programme', 'Natalie Ford', 'Chief Claims Officer', 'cycle time and leakage are under review'),
        (18, 'Cobalt Mining Group', 'Mining & Resources', 'maintenance execution', 'shutdown planning', 'asset data governance', 'production stability initiative', 'Ethan Cole', 'General Manager Operations', 'availability loss is reducing output'),
        (19, 'Arbor Social Housing', 'Housing & Community', 'repairs case management', 'tenant communications', 'housing management system integration', 'service recovery plan', 'Imani Price', 'Director of Customer Services', 'repair completion times are escalating'),
        (20, 'Brightline Consumer Goods', 'Consumer Products', 'demand planning', 'trade promotion execution', 'planning and distributor data integration', 'margin protection programme', 'Marco Silva', 'VP Commercial Operations', 'forecast error is eroding margin')
    ) AS vertical(number, company_prefix, industry, operating_area, opportunity, technology, trigger, persona_name, persona_title, pain_signal)
), variants AS (
    SELECT * FROM (VALUES
        ('Pacific', 'Operations'), ('Northern', 'Services'), ('Urban', 'Networks'), ('Coastal', 'Partners'), ('Summit', 'Group'),
        ('Cedar', 'Holdings'), ('Atlas', 'Collective'), ('Meridian', 'Alliance'), ('Pioneer', 'Enterprises'), ('Aurora', 'Works')
    ) AS variant(region, suffix)
), numbered AS (
    SELECT catalogue.*, series.n, variant.region, variant.suffix,
           row_number() OVER (ORDER BY catalogue.number, series.n) AS catalogue_number
    FROM catalogue
    CROSS JOIN generate_series(1, 100) AS series(n)
    JOIN variants variant ON variant.region = (ARRAY['Pacific','Northern','Urban','Coastal','Summit','Cedar','Atlas','Meridian','Pioneer','Aurora'])[((series.n - 1) % 10) + 1]
)
INSERT INTO leads (id, scenario_id, company_name, industry, public_description, difficulty, potential_value_range, decision_maker, technology_stack, budget_signal, pain_severity, created_at, updated_at, version)
SELECT
    ('70000000-0000-0000-0000-' || lpad(catalogue_number::text, 12, '0'))::uuid,
    ('60000000-0000-0000-0000-' || lpad(number::text, 12, '0'))::uuid,
    company_prefix || ' ' || region || ' ' || suffix || ' ' || lpad(n::text, 3, '0'),
    industry,
    'A ' || industry || ' organisation exploring ' || opportunity || ' to improve ' || operating_area || ' while protecting day-to-day delivery.',
    CASE WHEN n % 5 IN (1, 2) THEN 'EASY' WHEN n % 5 IN (3, 4) THEN 'MEDIUM' ELSE 'HARD' END,
    CASE WHEN n % 3 = 0 THEN '$2M - $5M' WHEN n % 3 = 1 THEN '$750K - $1.8M' ELSE '$1.2M - $3M' END,
    CASE WHEN n % 4 = 0 THEN 'Unconfirmed sponsor - stakeholder mapping required' ELSE persona_name || ', ' || persona_title END,
    technology || ' with legacy reporting workarounds',
    CASE WHEN n % 4 = 0 THEN 'No confirmed budget; validate commercial appetite' ELSE 'Funding is being evaluated alongside ' || trigger END,
    CASE WHEN n % 5 = 0 THEN 'High - ' || pain_signal ELSE 'Medium - ' || pain_signal END,
    NOW(), NOW(), 0
FROM numbered
ON CONFLICT (id) DO NOTHING;

WITH catalogue AS (
    SELECT * FROM (VALUES
        (1, 'airworthiness audit', 'dispatch reliability is under executive review'), (2, 'network resilience programme', 'reliability targets are tightening'), (3, 'ministerial service review', 'service standards review announced'), (4, 'major project gateway review', 'milestone slippage affects funding release'), (5, 'streaming platform relaunch', 'subscription retention is under pressure'),
        (6, 'brand experience initiative', 'guest satisfaction varies across properties'), (7, 'retention improvement programme', 'first-year retention target was missed'), (8, 'climate resilience investment', 'non-revenue water is rising'), (9, 'export compliance deadline', 'traceability evidence is required for export'), (10, 'care access improvement plan', 'patient wait times are increasing'),
        (11, 'plant performance programme', 'unplanned downtime is above target'), (12, 'risk remediation plan', 'remediation milestones are board tracked'), (13, 'peak season readiness', 'service reliability affects contract renewals'), (14, 'client service transformation', 'utilisation and response time vary by team'), (15, '5G rollout assurance', 'rollout dates are commercially committed'),
        (16, 'trial acceleration portfolio', 'protocol timelines are at risk'), (17, 'claims transformation programme', 'cycle time and leakage are under review'), (18, 'production stability initiative', 'availability loss is reducing output'), (19, 'service recovery plan', 'repair completion times are escalating'), (20, 'margin protection programme', 'forecast error is eroding margin')
    ) AS vertical(number, trigger, pain_signal)
), numbered AS (
    SELECT catalogue.*, series.n, row_number() OVER (ORDER BY catalogue.number, series.n) AS catalogue_number
    FROM catalogue CROSS JOIN generate_series(1, 100) AS series(n)
)
INSERT INTO lead_signals (id, lead_id, label, category)
SELECT
    ('71000000-0000-0000-0000-' || lpad(((catalogue_number - 1) * 2 + signal_offset)::text, 12, '0'))::uuid,
    ('70000000-0000-0000-0000-' || lpad(catalogue_number::text, 12, '0'))::uuid,
    CASE WHEN signal_offset = 1 THEN trigger ELSE pain_signal END,
    CASE WHEN signal_offset = 1 THEN 'BUSINESS_TRIGGER' ELSE 'OPERATIONAL' END
FROM numbered CROSS JOIN generate_series(1, 2) AS signal_offset
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_leads_catalog_active ON leads (scenario_id, industry, difficulty, company_name);
CREATE INDEX IF NOT EXISTS idx_leads_catalog_company_lower ON leads (lower(company_name));
