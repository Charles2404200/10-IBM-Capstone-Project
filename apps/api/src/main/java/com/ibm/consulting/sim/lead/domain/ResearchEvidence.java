package com.ibm.consulting.sim.lead.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A single piece of client-intelligence evidence collected by the learner
 * before outreach. Evidence carries traceable source metadata (URL, title,
 * date, confidence) so it can be audited and, when its {@link EvidenceType}
 * is {@code HYPOTHESIS}, may cite the other evidence rows that support it
 * via {@link #supportingEvidenceIds}.
 *
 * <p>Constructed exclusively through {@link Builder} (Builder pattern) — the
 * growing, partly-optional field set (note, hypothesis, source metadata,
 * confidence, supporting evidence) makes a telescoping static factory or a
 * multi-arg constructor unreadable and error-prone at call sites.
 */
@Entity
@Table(name = "research_evidence")
public class ResearchEvidence extends BaseEntity {

    @Column(nullable = false)
    private UUID engagementId;

    @Column(nullable = false)
    private UUID leadId;

    @Column(columnDefinition = "text", nullable = false)
    private String note;

    @Column(columnDefinition = "text")
    private String hypothesis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType evidenceType;

    @Column(length = 500)
    private String sourceUrl;

    @Column(length = 300)
    private String sourceTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceOrigin origin = EvidenceOrigin.USER_SUPPLIED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvidenceVerificationStatus verificationStatus = EvidenceVerificationStatus.UNVERIFIED;

    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfidenceLevel confidence;

    /** Stable per-engagement ordinal, rendered by the frontend as "E-01", "E-02", ... */
    @Column(nullable = false)
    private Integer sequenceNo;

    /** IDs of other evidence rows (same engagement) that support this hypothesis. */
    @ElementCollection
    @CollectionTable(name = "research_evidence_links", joinColumns = @JoinColumn(name = "evidence_id"))
    @Column(name = "linked_evidence_id")
    private Set<UUID> supportingEvidenceIds = new LinkedHashSet<>();

    protected ResearchEvidence() {}

    public static Builder builder() {
        return new Builder();
    }

    public UUID getEngagementId() { return engagementId; }
    public UUID getLeadId() { return leadId; }
    public String getNote() { return note; }
    public String getHypothesis() { return hypothesis; }
    public EvidenceType getEvidenceType() { return evidenceType; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceTitle() { return sourceTitle; }
    public EvidenceOrigin getOrigin() { return origin; }
    public EvidenceVerificationStatus getVerificationStatus() { return verificationStatus; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public ConfidenceLevel getConfidence() { return confidence; }
    public Integer getSequenceNo() { return sequenceNo; }
    public Set<UUID> getSupportingEvidenceIds() { return Set.copyOf(supportingEvidenceIds); }

    /** Builder for {@link ResearchEvidence} — see class Javadoc for rationale. */
    public static final class Builder {
        private final ResearchEvidence instance = new ResearchEvidence();

        public Builder engagementId(UUID engagementId) { instance.engagementId = engagementId; return this; }
        public Builder leadId(UUID leadId) { instance.leadId = leadId; return this; }
        public Builder note(String note) { instance.note = note; return this; }
        public Builder hypothesis(String hypothesis) { instance.hypothesis = hypothesis; return this; }
        public Builder evidenceType(EvidenceType evidenceType) { instance.evidenceType = evidenceType; return this; }
        public Builder sourceUrl(String sourceUrl) { instance.sourceUrl = sourceUrl; return this; }
        public Builder sourceTitle(String sourceTitle) { instance.sourceTitle = sourceTitle; return this; }
        public Builder origin(EvidenceOrigin origin) { instance.origin = origin; return this; }
        public Builder verificationStatus(EvidenceVerificationStatus status) { instance.verificationStatus = status; return this; }
        public Builder occurredOn(LocalDate occurredOn) { instance.occurredOn = occurredOn; return this; }
        public Builder confidence(ConfidenceLevel confidence) { instance.confidence = confidence; return this; }
        public Builder sequenceNo(int sequenceNo) { instance.sequenceNo = sequenceNo; return this; }
        public Builder supportingEvidenceIds(Set<UUID> ids) {
            instance.supportingEvidenceIds = ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
            return this;
        }

        public ResearchEvidence build() {
            if (instance.engagementId == null) throw new IllegalStateException("engagementId is required");
            if (instance.leadId == null) throw new IllegalStateException("leadId is required");
            if (instance.note == null || instance.note.isBlank()) throw new IllegalStateException("note is required");
            if (instance.evidenceType == null) throw new IllegalStateException("evidenceType is required");
            if (instance.sequenceNo == null) throw new IllegalStateException("sequenceNo is required");
            if (instance.confidence == null) instance.confidence = ConfidenceLevel.MEDIUM;
            if (instance.origin == null) instance.origin = EvidenceOrigin.USER_SUPPLIED;
            if (instance.verificationStatus == null) {
                instance.verificationStatus = instance.origin == EvidenceOrigin.USER_SUPPLIED
                        ? EvidenceVerificationStatus.UNVERIFIED
                        : EvidenceVerificationStatus.CORROBORATED;
            }
            return instance;
        }
    }
}
