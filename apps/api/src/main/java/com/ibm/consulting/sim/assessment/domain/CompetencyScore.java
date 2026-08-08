package com.ibm.consulting.sim.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Value object: a single competency's deterministic score with a short evidence citation. */
@Embeddable
public class CompetencyScore {

    @Column(name = "competency_name", nullable = false)
    private String competencyName;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "evidence_note", columnDefinition = "text")
    private String evidenceNote;

    protected CompetencyScore() {}

    public CompetencyScore(String competencyName, int score, String evidenceNote) {
        this.competencyName = competencyName;
        this.score = score;
        this.evidenceNote = evidenceNote;
    }

    public String getCompetencyName() { return competencyName; }
    public int getScore() { return score; }
    public String getEvidenceNote() { return evidenceNote; }
}
