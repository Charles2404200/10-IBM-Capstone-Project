package com.ibm.consulting.sim.scenario.domain;

import com.ibm.consulting.sim.engagement.domain.Engagement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLifecyclePersistenceTest {
    @Test
    void revisionCopiesDefinitionAndChangesDoNotReachThePublishedSource() {
        Scenario original = Scenario.create("Lifecycle", "Technology", "Description", 3);
        original.updateLifecycleDefinition("original definition");
        original.publish();
        Scenario revision = original.createRevision();
        assertThat(revision.getLifecycleDefinition()).isEqualTo("original definition");
        revision.updateLifecycleDefinition("revised definition");
        assertThat(original.getLifecycleDefinition()).isEqualTo("original definition");
        assertThatThrownBy(() -> original.updateLifecycleDefinition("changed"))
                .isInstanceOf(Scenario.ScenarioNotEditableException.class);
        revision.archive();
        assertThatThrownBy(() -> revision.updateLifecycleDefinition("changed"))
                .isInstanceOf(Scenario.ScenarioNotEditableException.class);
    }

    @Test
    void snapshotIsSetAtCreationAndLegacyFactoriesRemainCompatible() {
        UUID user = UUID.randomUUID(), scenario = UUID.randomUUID(), persona = UUID.randomUUID();
        Engagement original = Engagement.start(user, scenario, persona, "difficulty", null, "lifecycle");
        Engagement retry = Engagement.start(user, scenario, persona, original.getDifficultyProfileSnapshot(),
                original.getId(), original.getLifecycleDefinitionSnapshot());
        assertThat(retry.getLifecycleDefinitionSnapshot()).isEqualTo("lifecycle");
        assertThat(retry.getRetryOfEngagementId()).isEqualTo(original.getId());
        assertThat(Engagement.start(user, scenario, persona).getLifecycleDefinitionSnapshot()).isNull();
    }
}
