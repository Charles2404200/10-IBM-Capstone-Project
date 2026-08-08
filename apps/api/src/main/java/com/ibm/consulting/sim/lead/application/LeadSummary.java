package com.ibm.consulting.sim.lead.application;

import com.ibm.consulting.sim.lead.domain.Lead;
import com.ibm.consulting.sim.lead.domain.LeadSignal;

import java.util.List;
import java.util.UUID;

public record LeadSummary(
        UUID id,
        String companyName,
        String industry,
        String publicDescription,
        String difficulty,
        List<SignalRecord> signals) {

    public record SignalRecord(UUID id, String label, String category) {
        static SignalRecord from(LeadSignal s) { return new SignalRecord(s.getId(), s.getLabel(), s.getCategory()); }
    }

    public static LeadSummary from(Lead l) {
        return new LeadSummary(l.getId(), l.getCompanyName(), l.getIndustry(),
                l.getPublicDescription(), l.getDifficulty().name(),
                l.getSignals().stream().map(SignalRecord::from).toList());
    }
}
