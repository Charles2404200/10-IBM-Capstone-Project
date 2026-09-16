import { useMemo, useState } from 'react'
import { Button, InlineLoading, Modal, Tag } from '@carbon/react'
import { Document, Light } from '@carbon/icons-react'
import { useResearch } from '@/api/hooks/useLeads'
import type { EvidenceVerificationStatus, ResearchEvidence } from '@/api/types'
import styles from '../lifecycle.module.scss'

const verificationTag: Record<EvidenceVerificationStatus, 'green' | 'cyan' | 'warm-gray' | 'red'> = {
  VERIFIED: 'green',
  CORROBORATED: 'cyan',
  UNVERIFIED: 'warm-gray',
  CONTRADICTED: 'red',
}

function evidenceCode(sequenceNo: number) {
  return `E-${String(sequenceNo).padStart(2, '0')}`
}

function sourceLabel(item: ResearchEvidence) {
  return item.sourceTitle ?? item.evidenceType.replace(/_/g, ' ')
}

export default function EngagementEvidenceHint({ engagementId }: { engagementId: string }) {
  const [open, setOpen] = useState(false)
  const { data: evidence, isLoading } = useResearch(engagementId)
  const collectedEvidence = useMemo(() => evidence ?? [], [evidence])

  const problems = useMemo(
    () => collectedEvidence
      .filter((item) => item.evidenceType === 'HYPOTHESIS')
      .sort((left, right) => right.sequenceNo - left.sequenceNo),
    [collectedEvidence],
  )
  const usableEvidence = useMemo(
    () => collectedEvidence
      .filter((item) => item.evidenceType !== 'HYPOTHESIS' && item.verificationStatus !== 'CONTRADICTED')
      .sort((left, right) => right.sequenceNo - left.sequenceNo),
    [collectedEvidence],
  )
  const codeById = useMemo(
    () => new Map(collectedEvidence.map((item) => [item.id, evidenceCode(item.sequenceNo)])),
    [collectedEvidence],
  )
  const currentProblem = problems[0]

  return (
    <div className={styles.evidenceHint}>
      <Button
        className={styles.evidenceHintButton}
        hasIconOnly
        kind="primary"
        renderIcon={Light}
        iconDescription="Open engagement evidence"
        onClick={() => setOpen(true)}
        tooltipPosition="left"
      />
      <span className={styles.evidenceHintCount} aria-label={`${usableEvidence.length} usable evidence items`}>
        {usableEvidence.length}
      </span>

      <Modal
        className={styles.evidenceModal}
        open={open}
        passiveModal
        modalHeading="Engagement evidence"
        onRequestClose={() => setOpen(false)}
        size="lg"
      >
        <div className={styles.evidenceModalIntro}>
          <div>
            <p className={styles.evidenceModalEyebrow}>Working brief</p>
            <p>Use this record to keep outreach, meeting decisions and your proposal grounded in what you have actually discovered.</p>
          </div>
          <span className={styles.evidenceCountLabel}>{usableEvidence.length} usable</span>
        </div>

        {isLoading ? <InlineLoading description="Loading engagement evidence" /> : (
          <div className={styles.evidenceModalSections}>
            <section className={styles.identifiedProblem} aria-labelledby="identified-problem-heading">
              <div className={styles.evidenceSectionHeading}>
                <div><p className={styles.evidenceModalEyebrow}>Current view</p><h3 id="identified-problem-heading">Problem identified</h3></div>
                {currentProblem && <Tag type="blue" size="sm">Hypothesis</Tag>}
              </div>
              {currentProblem ? (
                <>
                  <p className={styles.problemStatement}>{currentProblem.hypothesis ?? currentProblem.note}</p>
                  {currentProblem.supportingEvidenceIds.length > 0 && (
                    <p className={styles.problemSupport}>
                      Grounded in {currentProblem.supportingEvidenceIds.map((id) => codeById.get(id) ?? 'evidence').join(', ')}
                    </p>
                  )}
                </>
              ) : (
                <p className={styles.evidenceEmpty}>No problem hypothesis has been saved yet. Gather signals, then name the client problem when the pattern is clear.</p>
              )}
            </section>

            <section aria-labelledby="usable-evidence-heading">
              <div className={styles.evidenceSectionHeading}>
                <div><p className={styles.evidenceModalEyebrow}>Evidence register</p><h3 id="usable-evidence-heading">Evidence you can use</h3></div>
                <span className={styles.evidenceCountLabel}>{usableEvidence.length} items</span>
              </div>
              {usableEvidence.length > 0 ? (
                <div className={styles.evidenceRegister}>
                  {usableEvidence.map((item) => (
                    <article className={styles.evidenceRegisterItem} key={item.id}>
                      <div className={styles.evidenceRegisterMeta}>
                        <span className={styles.evidenceReference}>{evidenceCode(item.sequenceNo)}</span>
                        <span>{sourceLabel(item)}</span>
                        <Tag type={verificationTag[item.verificationStatus]} size="sm">{item.verificationStatus.toLowerCase()}</Tag>
                      </div>
                      <p>{item.note}</p>
                      <div className={styles.evidenceRegisterFooter}>
                        <span>{item.reasoningLane?.replace(/_/g, ' ').toLowerCase() ?? 'general signal'}</span>
                        <span>{item.confidence.toLowerCase()} confidence</span>
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <div className={styles.evidenceEmptyState}><Document size={24} /><p>No usable evidence yet. Evidence captured during research and the meeting will appear here.</p></div>
              )}
            </section>
          </div>
        )}
      </Modal>
    </div>
  )
}