package com.ibm.consulting.sim.assessment.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID engagementId;

    @ElementCollection
    @CollectionTable(name = "assessment_competency_scores", joinColumns = @JoinColumn(name = "assessment_id"))
    private List<CompetencyScore> competencyScores = new ArrayList<>();

    @Column(nullable = false)
    private int overallScore;

    @Column(nullable = false)
    private String outcome;

    @Column(columnDefinition = "text")
    private String feedbackSummary;

    @ElementCollection
    @CollectionTable(name = "assessment_strengths", joinColumns = @JoinColumn(name = "assessment_id"))
    @Column(name = "item", columnDefinition = "text")
    private List<String> strengths = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "assessment_improvement_areas", joinColumns = @JoinColumn(name = "assessment_id"))
    @Column(name = "item", columnDefinition = "text")
    private List<String> improvementAreas = new ArrayList<>();

    @Column(nullable = false)
    private Instant generatedAt;

    protected Assessment() {}

    public static Assessment create(UUID engagementId, List<CompetencyScore> competencyScores, int overallScore,
                                     String outcome, String feedbackSummary,
                                     List<String> strengths, List<String> improvementAreas) {
        Assessment a = new Assessment();
        a.engagementId = engagementId;
        a.competencyScores = new ArrayList<>(competencyScores);
        a.overallScore = overallScore;
        a.outcome = outcome;
        a.feedbackSummary = feedbackSummary;
        a.strengths = new ArrayList<>(strengths);
        a.improvementAreas = new ArrayList<>(improvementAreas);
        a.generatedAt = Instant.now();
        return a;
    }

    public UUID getEngagementId() { return engagementId; }
    public List<CompetencyScore> getCompetencyScores() { return Collections.unmodifiableList(competencyScores); }
    public int getOverallScore() { return overallScore; }
    public String getOutcome() { return outcome; }
    public String getFeedbackSummary() { return feedbackSummary; }
    public List<String> getStrengths() { return Collections.unmodifiableList(strengths); }
    public List<String> getImprovementAreas() { return Collections.unmodifiableList(improvementAreas); }
    public Instant getGeneratedAt() { return generatedAt; }
}
