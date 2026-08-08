-- Demo scenario: Healthcare Digital Transformation
INSERT INTO scenarios (id, title, industry, description, status, difficulty, content_version,
                        created_at, updated_at, version)
VALUES (
    'a1b2c3d4-0000-0000-0000-000000000001',
    'MediCare Digital Transformation',
    'Healthcare',
    'MediCare Regional Hospital Network is struggling with fragmented patient data systems across 12 hospitals. The CIO is under pressure to modernise before a major regulatory audit. They have budget but are burned by a failed EHR migration 3 years ago.',
    'ACTIVE',
    2,
    1,
    NOW(), NOW(), 0
);

-- Primary persona: Sarah Chen, CIO
INSERT INTO personas (id, scenario_id, name, job_title, organisation, communication_style,
                       visible_concerns, hidden_concerns, business_goals, prompt_version,
                       created_at, updated_at, version)
VALUES (
    'b2c3d4e5-0000-0000-0000-000000000001',
    'a1b2c3d4-0000-0000-0000-000000000001',
    'Sarah Chen',
    'Chief Information Officer',
    'MediCare Regional Hospital Network',
    'Direct, analytical, low patience for buzzwords. Asks for evidence before committing. Responds well to peers who have faced similar problems.',
    'Data integration across 12 sites; upcoming regulatory audit; budget justification to board',
    'Career risk from another failed project; trust deficit with IT vendors; personal deadline pressure',
    'Unified patient data platform before Q4 audit; clear ROI model for board; minimal disruption to clinical staff',
    1,
    NOW(), NOW(), 0
);

-- Lead 1: Easy
INSERT INTO leads (id, scenario_id, company_name, industry, public_description, difficulty,
                    created_at, updated_at, version)
VALUES (
    'c3d4e5f6-0000-0000-0000-000000000001',
    'a1b2c3d4-0000-0000-0000-000000000001',
    'MediCare Regional Hospital Network',
    'Healthcare',
    'A 12-hospital regional network facing data integration challenges ahead of a regulatory review.',
    'EASY',
    NOW(), NOW(), 0
);

INSERT INTO lead_signals (id, lead_id, label, category) VALUES
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000001', 'Regulatory audit in 90 days', 'COMPLIANCE'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000001', 'Recent CIO appointment (8 months)', 'LEADERSHIP_CHANGE'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000001', 'Budget approved for IT modernisation', 'FINANCIAL_SIGNAL'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000001', 'Failed EHR migration 2021', 'RISK_INDICATOR');

-- Lead 2: Hard
INSERT INTO leads (id, scenario_id, company_name, industry, public_description, difficulty,
                    created_at, updated_at, version)
VALUES (
    'c3d4e5f6-0000-0000-0000-000000000002',
    'a1b2c3d4-0000-0000-0000-000000000001',
    'PharmaCo Distribution Ltd',
    'Pharmaceuticals',
    'A mid-size pharmaceutical distributor looking to improve supply chain visibility. Limited budget context available.',
    'HARD',
    NOW(), NOW(), 0
);

INSERT INTO lead_signals (id, lead_id, label, category) VALUES
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000002', 'Supply chain disruption Q1', 'OPERATIONAL'),
    (gen_random_uuid(), 'c3d4e5f6-0000-0000-0000-000000000002', 'New COO from logistics background', 'LEADERSHIP_CHANGE');
