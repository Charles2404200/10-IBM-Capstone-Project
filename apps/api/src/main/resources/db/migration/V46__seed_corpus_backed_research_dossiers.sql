-- Every existing scenario receives four governed research corpora. Each lane
-- is independently scoped so a reader cannot learn financial or technical
-- detail through company-news or stakeholder research.
WITH lanes(collection, lane_label, lens) AS (
    VALUES
        ('RESEARCH_COMPANY_NEWS', 'operating news', 'executive pressure, delivery context and public operating signals'),
        ('RESEARCH_STAKEHOLDER', 'stakeholder intelligence', 'decision rights, stated priorities and constraints'),
        ('RESEARCH_FINANCIAL', 'commercial intelligence', 'financial exposure, approval controls and investment signals'),
        ('RESEARCH_TECHNOLOGY', 'technology due diligence', 'systems, data hand-offs, control points and operating dependencies')
), focuses(position, headline, topic, detail) AS (
    VALUES
        (1, 'Executive operating context', 'the leadership operating context', 'how priority signals enter the relevant decision record'),
        (2, 'Service and delivery pressure', 'the pressure on day-to-day delivery', 'where service expectations meet the current operating condition'),
        (3, 'Decision timing', 'the timing of the next material decision', 'the reporting cadence and event sequence surrounding that decision'),
        (4, 'Accountability boundary', 'the boundary between accountable roles', 'where ownership is visible and where it still needs confirmation'),
        (5, 'Information hand-off', 'the movement of information between teams', 'which record changes hands before the issue is visible'),
        (6, 'Control condition', 'the control condition around the workflow', 'the checks that must remain dependable while information is reviewed'),
        (7, 'Reliability exposure', 'the exposure to unreliable delivery', 'how the reported condition affects dependable operating performance'),
        (8, 'Exception pattern', 'the pattern of exceptions in the workflow', 'the points at which normal handling gives way to local workarounds'),
        (9, 'Planning horizon', 'the planning horizon for the work', 'how near-term choices constrain later operating options'),
        (10, 'Workflow ownership', 'the ownership of the critical workflow', 'the distinction between contributing teams and the accountable decision role'),
        (11, 'Evidence quality', 'the quality of evidence available to the client', 'which source records are sufficiently consistent to support comparison'),
        (12, 'Cross-functional dependency', 'the dependency across operating functions', 'how a local record affects a decision made elsewhere'),
        (13, 'Escalation path', 'the route by which issues are escalated', 'the decision threshold that turns an observation into a management concern'),
        (14, 'Performance visibility', 'the visibility of current performance', 'the measures and records that describe the condition before action is taken'),
        (15, 'Commercial materiality', 'the materiality of the client signal', 'the distinction between a confirmed exposure and an assumed financial outcome'),
        (16, 'Operational resilience', 'the resilience of the operating process', 'the safeguards required when teams handle exceptions or changing priorities'),
        (17, 'Data lineage', 'the lineage of the decision-relevant record', 'how information can be traced through the systems and teams that use it'),
        (18, 'Prioritisation logic', 'the logic used to prioritise work', 'which reported signals currently influence attention and sequencing'),
        (19, 'Delivery constraint', 'the constraint on changing the workflow', 'the conditions that limit disruption to the current operation'),
        (20, 'Assurance requirement', 'the assurance requirement for any change', 'the controls and evidence needed before a decision can be relied upon'),
        (21, 'Change tolerance', 'the organisation''s tolerance for change', 'the difference between a contained adjustment and a wider operating commitment'),
        (22, 'Decision record', 'the record supporting the decision', 'how reported facts, assumptions and unresolved questions need to remain distinct'),
        (23, 'Risk signal', 'the risk signal visible in the current record', 'the observable condition that warrants attention without proving causation'),
        (24, 'Validation boundary', 'the boundary between reported evidence and a hypothesis', 'the specific unknown that must be tested before a conclusion is accepted')
), passages AS (
    SELECT s.id AS scenario_id,
           lanes.collection,
           focuses.position,
           format(
               '%s. The %s record treats %s as a distinct reported signal for %s. Scenario material records: %s. The passage concentrates on %s through the lens of %s, with attention to %s. It traces information, hand-offs, accountable roles, timing and control boundaries through which the signal becomes material. It does not state a proven cause, approved intervention, financial commitment or realised outcome. The connected evidence question is %s. This remains a separate record, intended for reconciliation with corroborating evidence before a broader conclusion is drawn.',
               focuses.headline,
               lanes.lane_label,
               focuses.topic,
               s.title,
               CASE focuses.position % 6
                   WHEN 1 THEN s.business_situation
                   WHEN 2 THEN s.observable_symptom
                   WHEN 3 THEN s.consulting_mandate
                   WHEN 4 THEN s.description
                   WHEN 5 THEN s.objective
                   ELSE COALESCE(NULLIF(replace(s.success_criteria, '|', '; '), ''), s.business_situation)
               END,
               focuses.detail,
               lanes.lens,
               focuses.topic,
               COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', (focuses.position - 1) % 4 + 1), ''),
                        'which scenario evidence must be corroborated before a decision is made')
           ) AS content
    FROM scenarios s
    CROSS JOIN lanes
    CROSS JOIN focuses
), documents AS (
    SELECT scenario_id,
           collection,
           ('d4600000-' || substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 1, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 5, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 9, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 13, 12))::uuid AS id,
           string_agg(content, E'\n\n' ORDER BY position) AS source_text
    FROM passages
    GROUP BY scenario_id, collection
)
INSERT INTO knowledge_documents (id, scenario_id, persona_id, collection, title, source_text, created_at, updated_at, version)
SELECT documents.id,
       documents.scenario_id,
       NULL,
       documents.collection,
       scenarios.title || ' ' || lower(replace(documents.collection, 'RESEARCH_', '')) || ' dossier',
       documents.source_text,
       NOW(), NOW(), 0
FROM documents
JOIN scenarios ON scenarios.id = documents.scenario_id
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_documents existing
    WHERE existing.scenario_id = documents.scenario_id
      AND existing.persona_id IS NULL
      AND existing.collection = documents.collection
);

WITH lanes(collection, lane_label, lens) AS (
    VALUES
        ('RESEARCH_COMPANY_NEWS', 'operating news', 'executive pressure, delivery context and public operating signals'),
        ('RESEARCH_STAKEHOLDER', 'stakeholder intelligence', 'decision rights, stated priorities and constraints'),
        ('RESEARCH_FINANCIAL', 'commercial intelligence', 'financial exposure, approval controls and investment signals'),
        ('RESEARCH_TECHNOLOGY', 'technology due diligence', 'systems, data hand-offs, control points and operating dependencies')
), focuses(position, headline, topic, detail) AS (
    VALUES
        (1, 'Executive operating context', 'the leadership operating context', 'how priority signals enter the relevant decision record'),
        (2, 'Service and delivery pressure', 'the pressure on day-to-day delivery', 'where service expectations meet the current operating condition'),
        (3, 'Decision timing', 'the timing of the next material decision', 'the reporting cadence and event sequence surrounding that decision'),
        (4, 'Accountability boundary', 'the boundary between accountable roles', 'where ownership is visible and where it still needs confirmation'),
        (5, 'Information hand-off', 'the movement of information between teams', 'which record changes hands before the issue is visible'),
        (6, 'Control condition', 'the control condition around the workflow', 'the checks that must remain dependable while information is reviewed'),
        (7, 'Reliability exposure', 'the exposure to unreliable delivery', 'how the reported condition affects dependable operating performance'),
        (8, 'Exception pattern', 'the pattern of exceptions in the workflow', 'the points at which normal handling gives way to local workarounds'),
        (9, 'Planning horizon', 'the planning horizon for the work', 'how near-term choices constrain later operating options'),
        (10, 'Workflow ownership', 'the ownership of the critical workflow', 'the distinction between contributing teams and the accountable decision role'),
        (11, 'Evidence quality', 'the quality of evidence available to the client', 'which source records are sufficiently consistent to support comparison'),
        (12, 'Cross-functional dependency', 'the dependency across operating functions', 'how a local record affects a decision made elsewhere'),
        (13, 'Escalation path', 'the route by which issues are escalated', 'the decision threshold that turns an observation into a management concern'),
        (14, 'Performance visibility', 'the visibility of current performance', 'the measures and records that describe the condition before action is taken'),
        (15, 'Commercial materiality', 'the materiality of the client signal', 'the distinction between a confirmed exposure and an assumed financial outcome'),
        (16, 'Operational resilience', 'the resilience of the operating process', 'the safeguards required when teams handle exceptions or changing priorities'),
        (17, 'Data lineage', 'the lineage of the decision-relevant record', 'how information can be traced through the systems and teams that use it'),
        (18, 'Prioritisation logic', 'the logic used to prioritise work', 'which reported signals currently influence attention and sequencing'),
        (19, 'Delivery constraint', 'the constraint on changing the workflow', 'the conditions that limit disruption to the current operation'),
        (20, 'Assurance requirement', 'the assurance requirement for any change', 'the controls and evidence needed before a decision can be relied upon'),
        (21, 'Change tolerance', 'the organisation''s tolerance for change', 'the difference between a contained adjustment and a wider operating commitment'),
        (22, 'Decision record', 'the record supporting the decision', 'how reported facts, assumptions and unresolved questions need to remain distinct'),
        (23, 'Risk signal', 'the risk signal visible in the current record', 'the observable condition that warrants attention without proving causation'),
        (24, 'Validation boundary', 'the boundary between reported evidence and a hypothesis', 'the specific unknown that must be tested before a conclusion is accepted')
), passages AS (
    SELECT s.id AS scenario_id,
           lanes.collection,
           focuses.position,
           format(
               '%s. The %s record treats %s as a distinct reported signal for %s. Scenario material records: %s. The passage concentrates on %s through the lens of %s, with attention to %s. It traces information, hand-offs, accountable roles, timing and control boundaries through which the signal becomes material. It does not state a proven cause, approved intervention, financial commitment or realised outcome. The connected evidence question is %s. This remains a separate record, intended for reconciliation with corroborating evidence before a broader conclusion is drawn.',
               focuses.headline, lanes.lane_label, focuses.topic, s.title,
               CASE focuses.position % 6
                   WHEN 1 THEN s.business_situation WHEN 2 THEN s.observable_symptom
                   WHEN 3 THEN s.consulting_mandate WHEN 4 THEN s.description
                   WHEN 5 THEN s.objective
                   ELSE COALESCE(NULLIF(replace(s.success_criteria, '|', '; '), ''), s.business_situation)
               END,
               focuses.detail, lanes.lens, focuses.topic,
               COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', (focuses.position - 1) % 4 + 1), ''),
                        'which scenario evidence must be corroborated before a decision is made')
           ) AS content
    FROM scenarios s CROSS JOIN lanes CROSS JOIN focuses
), documents AS (
    SELECT scenario_id, collection,
           ('d4600000-' || substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 1, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 5, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 9, 4) || '-' ||
            substr(md5('research-dossier:' || scenario_id::text || ':' || collection), 13, 12))::uuid AS document_id,
           position, content
    FROM passages
)
INSERT INTO document_chunks (id, document_id, scenario_id, persona_id, collection, chunk_index, content, embedding, created_at, updated_at, version)
SELECT ('d4610000-' || substr(md5('research-passage:' || document_id::text || ':' || position), 1, 4) || '-' ||
        substr(md5('research-passage:' || document_id::text || ':' || position), 5, 4) || '-' ||
        substr(md5('research-passage:' || document_id::text || ':' || position), 9, 4) || '-' ||
        substr(md5('research-passage:' || document_id::text || ':' || position), 13, 12))::uuid,
       document_id, scenario_id, NULL, collection, position - 1, content, '0.0', NOW(), NOW(), 0
FROM documents
WHERE EXISTS (SELECT 1 FROM knowledge_documents document WHERE document.id = documents.document_id)
ON CONFLICT (id) DO NOTHING;