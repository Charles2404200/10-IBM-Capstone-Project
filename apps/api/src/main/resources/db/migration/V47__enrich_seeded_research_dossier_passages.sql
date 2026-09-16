-- V46 established corpus scope and provenance. Replace its deliberately compact
-- bootstrap prose with article-length, lane-specific passages. All text is
-- derived from the scenario's approved fields; it introduces no external client
-- facts, people, dates, figures, suppliers or outcomes.
WITH focuses(position, headline, topic, detail) AS (
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
), refreshed_passages AS (
    SELECT s.id AS scenario_id,
           c.collection,
           focuses.position,
           format(
               '%s. %s for %s examines %s. Scenario record: %s. %s It reviews %s, including %s. %s The evidence question is %s. This passage keeps a reported condition separate from an operating dependency and a hypothesis; it establishes neither a cause, owner, approval, solution nor outcome. It records the relevant decision boundary and supports a later comparison of corroborated evidence.',
               focuses.headline,
               CASE c.collection
                   WHEN 'RESEARCH_COMPANY_NEWS' THEN 'Company reporting'
                   WHEN 'RESEARCH_STAKEHOLDER' THEN 'Stakeholder reporting'
                   WHEN 'RESEARCH_FINANCIAL' THEN 'Commercial reporting'
                   ELSE 'Technology reporting'
               END,
               s.title,
               focuses.topic,
               CASE
                   WHEN c.collection = 'RESEARCH_COMPANY_NEWS' THEN CASE focuses.position % 4
                       WHEN 1 THEN s.business_situation WHEN 2 THEN s.observable_symptom
                       WHEN 3 THEN s.description ELSE s.objective END
                   WHEN c.collection = 'RESEARCH_STAKEHOLDER' THEN CASE focuses.position % 4
                       WHEN 1 THEN s.consulting_mandate WHEN 2 THEN s.business_situation
                       WHEN 3 THEN s.objective ELSE split_part(s.unknowns_to_validate, '|', (focuses.position - 1) % 4 + 1) END
                   WHEN c.collection = 'RESEARCH_FINANCIAL' THEN CASE focuses.position % 4
                       WHEN 1 THEN s.objective WHEN 2 THEN s.business_situation
                       WHEN 3 THEN COALESCE(NULLIF(replace(s.success_criteria, '|', '; '), ''), s.description)
                       ELSE s.observable_symptom END
                   ELSE CASE focuses.position % 4
                       WHEN 1 THEN s.observable_symptom WHEN 2 THEN s.consulting_mandate
                       WHEN 3 THEN s.description ELSE s.business_situation END
               END,
               CASE c.collection
                   WHEN 'RESEARCH_COMPANY_NEWS' THEN 'It separates the public operating narrative from the unverified mechanisms behind it, retaining the delivery context and executive pressure without presenting either as a diagnosis.'
                   WHEN 'RESEARCH_STAKEHOLDER' THEN 'It separates a stated consulting mandate from an individual decision maker''s private view, preserving the role, influence and decision-right questions that the scenario does not yet confirm.'
                   WHEN 'RESEARCH_FINANCIAL' THEN 'It separates a material commercial signal from a confirmed budget, business case, approval, contract value or realised saving, none of which is established by this record.'
                   ELSE 'It separates the documented system and information environment from a root-cause claim, target design or implementation commitment, none of which is established by this record.'
               END,
               focuses.detail,
               CASE c.collection
                   WHEN 'RESEARCH_COMPANY_NEWS' THEN 'the relationship between the reported operating condition and the organisation''s public delivery narrative'
                   WHEN 'RESEARCH_STAKEHOLDER' THEN 'the relationship between stated priorities, accountable roles and the decision path'
                   WHEN 'RESEARCH_FINANCIAL' THEN 'the relationship between operational exposure, decision timing and commercial materiality'
                   ELSE 'the relationship between systems, records, controls and the operating hand-off'
               END,
               CASE c.collection
                   WHEN 'RESEARCH_COMPANY_NEWS' THEN 'No event, quote, external publication or performance result is implied beyond the scenario record.'
                   WHEN 'RESEARCH_STAKEHOLDER' THEN 'No named stakeholder position, approval or resistance is implied beyond the scenario record.'
                   WHEN 'RESEARCH_FINANCIAL' THEN 'No amount, forecast, committed investment or benefit result is implied beyond the scenario record.'
                   ELSE 'No system defect, integration failure, data issue or target architecture is implied beyond the scenario record.'
               END,
               COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', (focuses.position - 1) % 4 + 1), ''),
                        'which approved scenario evidence must be corroborated before a decision is made')
           ) AS content
    FROM scenarios s
    CROSS JOIN (VALUES ('RESEARCH_COMPANY_NEWS'), ('RESEARCH_STAKEHOLDER'),
                        ('RESEARCH_FINANCIAL'), ('RESEARCH_TECHNOLOGY')) AS c(collection)
    CROSS JOIN focuses
), target_chunks AS (
    SELECT chunks.id, refreshed_passages.content
    FROM document_chunks chunks
    JOIN refreshed_passages ON refreshed_passages.scenario_id = chunks.scenario_id
                            AND refreshed_passages.collection = chunks.collection
                            AND refreshed_passages.position = chunks.chunk_index + 1
    WHERE chunks.persona_id IS NULL
      AND chunks.collection IN ('RESEARCH_COMPANY_NEWS', 'RESEARCH_STAKEHOLDER',
                                 'RESEARCH_FINANCIAL', 'RESEARCH_TECHNOLOGY')
)
UPDATE document_chunks chunks
SET content = target_chunks.content,
    updated_at = NOW(),
    version = chunks.version + 1
FROM target_chunks
WHERE chunks.id = target_chunks.id;

UPDATE knowledge_documents documents
SET source_text = refreshed.source_text,
    updated_at = NOW(),
    version = documents.version + 1
FROM (
    SELECT chunks.document_id,
           string_agg(chunks.content, E'\n\n' ORDER BY chunks.chunk_index) AS source_text
    FROM document_chunks chunks
    WHERE chunks.persona_id IS NULL
      AND chunks.collection IN ('RESEARCH_COMPANY_NEWS', 'RESEARCH_STAKEHOLDER',
                                 'RESEARCH_FINANCIAL', 'RESEARCH_TECHNOLOGY')
    GROUP BY chunks.document_id
) refreshed
WHERE documents.id = refreshed.document_id;