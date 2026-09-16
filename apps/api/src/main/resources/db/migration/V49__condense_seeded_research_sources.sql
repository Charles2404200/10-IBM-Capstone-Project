-- A four-lane reader should be concise enough for an active research workflow.
-- Retain four independently selectable passages per lane, or approximately one
-- thousand words across Company News, Stakeholder, Financial and Technology.
WITH concise_passages AS (
    SELECT chunks.id,
           CASE chunks.collection
               WHEN 'RESEARCH_COMPANY_NEWS' THEN CASE chunks.chunk_index
                   WHEN 0 THEN format('Executive watch for %s. %s The report places this leadership pressure beside the current operating picture, without claiming an external announcement, market outcome or completed response. The open evidence question is %s.', s.title, s.business_situation, COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', 1), ''), 'which client signal requires corroboration'))
                   WHEN 1 THEN format('Operating report. %s This section records the visible delivery condition and its immediate business setting. It does not establish a root cause, accountable owner or preferred intervention; those remain separate questions in the client record.', s.observable_symptom)
                   WHEN 2 THEN format('Decision desk. %s The published operating narrative identifies a decision context, not a decision made. The reader should distinguish reported pressure from any assumed sponsor, commitment, timing or outcome.', s.consulting_mandate)
                   ELSE format('Evidence watch. %s The company account keeps the public description and the problem frame connected while reserving technical, financial and stakeholder detail for their dedicated research sources.', s.description)
               END
               WHEN 'RESEARCH_STAKEHOLDER' THEN CASE chunks.chunk_index
                   WHEN 0 THEN format('Mandate file for %s. %s This stakeholder record begins with the organisation''s stated consulting mandate, without assigning personal sponsorship, authority or resistance to an unnamed individual.', s.title, s.consulting_mandate)
                   WHEN 1 THEN format('Priority context. %s The stated business situation frames the decision environment. It does not confirm who owns the trade-off, controls funding or speaks for every affected operating team.', s.business_situation)
                   WHEN 2 THEN format('Influence boundary. %s The scenario identifies an operating concern that may shape stakeholder priorities. Decision rights, success measures and approval pathways still need direct validation.', s.observable_symptom)
                   ELSE format('Question on record. %s This profile preserves the unknown as a stakeholder-research question rather than presenting an inferred role, position or commitment as a source fact.', COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', 2), ''), 'Which stakeholder can validate the decision context?'))
               END
               WHEN 'RESEARCH_FINANCIAL' THEN CASE chunks.chunk_index
                   WHEN 0 THEN format('Commercial frame for %s. %s This note identifies the outcome the scenario expects to improve, while withholding a financial baseline, approved business case, forecast or realised benefit.', s.title, s.objective)
                   WHEN 1 THEN format('Materiality signal. %s The business situation makes the operating condition commercially relevant, but it does not itself confirm a budget, contract value, investment timetable or purchasing decision.', s.business_situation)
                   WHEN 2 THEN format('Measurement record. %s These stated success criteria identify what a credible outcome would need to demonstrate. They are not evidence of current performance or an agreed financial target.', COALESCE(NULLIF(replace(s.success_criteria, '|', '; '), ''), s.consulting_mandate))
                   ELSE format('Funding boundary. %s The financial research lane keeps commercial exposure separate from any assumed approval, supplier choice or benefit claim. The outstanding question is %s.', s.observable_symptom, COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', 3), ''), 'which measure is credible enough to support a decision'))
               END
               ELSE CASE chunks.chunk_index
                   WHEN 0 THEN format('Current-state brief for %s. %s This technical source records the observable condition before attributing it to a system defect, integration failure or data-quality problem.', s.title, s.observable_symptom)
                   WHEN 1 THEN format('Information-flow context. %s The mandate points to hand-offs that require examination. It does not identify a target architecture, technical owner or implementation sequence.', s.consulting_mandate)
                   WHEN 2 THEN format('Control boundary. %s The scenario description establishes the operating setting for systems and records. Existing controls must be understood before any technical change is assumed.', s.description)
                   ELSE format('Architecture question. %s This passage retains the technology-research unknown as a validation point, separating documented information conditions from a root-cause or solution claim.', COALESCE(NULLIF(split_part(s.unknowns_to_validate, '|', 4), ''), 'which dependency is least visible in the current operating record'))
               END
           END AS content
    FROM document_chunks chunks
    JOIN scenarios s ON s.id = chunks.scenario_id
        WHERE chunks.id::text LIKE 'd4610000-%%'
      AND chunks.chunk_index < 4
      AND chunks.collection IN ('RESEARCH_COMPANY_NEWS', 'RESEARCH_STAKEHOLDER',
                                'RESEARCH_FINANCIAL', 'RESEARCH_TECHNOLOGY')
)
UPDATE document_chunks chunks
SET content = concise_passages.content,
    updated_at = NOW(),
    version = chunks.version + 1
FROM concise_passages
WHERE chunks.id = concise_passages.id;

DELETE FROM document_chunks
WHERE id::text LIKE 'd4610000-%%'
  AND chunk_index >= 4
  AND collection IN ('RESEARCH_COMPANY_NEWS', 'RESEARCH_STAKEHOLDER',
                     'RESEARCH_FINANCIAL', 'RESEARCH_TECHNOLOGY');

UPDATE knowledge_documents documents
SET source_text = refreshed.source_text,
        updated_at = NOW(),
        version = documents.version + 1
FROM (
        SELECT chunks.document_id,
                     string_agg(chunks.content, E'\n\n' ORDER BY chunks.chunk_index) AS source_text
        FROM document_chunks chunks
        WHERE chunks.id::text LIKE 'd4610000-%%'
            AND chunks.chunk_index < 4
            AND chunks.collection IN ('RESEARCH_COMPANY_NEWS', 'RESEARCH_STAKEHOLDER',
                                                                'RESEARCH_FINANCIAL', 'RESEARCH_TECHNOLOGY')
        GROUP BY chunks.document_id
) refreshed
WHERE documents.id = refreshed.document_id;
