package com.ibm.consulting.sim.achievement.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.consulting.sim.achievement.domain.Achievement;
import com.ibm.consulting.sim.achievement.domain.AchievementRepository;
import com.ibm.consulting.sim.achievement.infrastructure.AchievementRuleCodec;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

@ExtendWith(MockitoExtension.class)
class AdminAchievementServiceTest {
    @Mock AchievementRepository achievementRepository;
    @Mock AchievementRuleMapper ruleMapper;
    @Mock AchievementRuleCodec ruleCodec;
    @Mock AuditLogger auditLogger;

    @InjectMocks AdminAchievementService service;

    // audit logging - deactivate achievement
    @Test
    void logsAchievementDeactivated() {
        UUID achievementId = UUID.randomUUID();

        // mock existing achievement
        Achievement achievement = mock(Achievement.class);
        when(achievement.getId()).thenReturn(achievementId);
        when(achievement.getRuleJson()).thenReturn("{}");
        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(ruleCodec.decode(any())).thenReturn(null);

        // deactivate achievement
        service.setActive(achievementId, false);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_ACHIEVEMENT_DEACTIVATED),
            eq("ACHIEVEMENT"),
            eq(achievementId.toString()));
    }

    // audit logging - reactivate achievement
    @Test
    void logsAchievementReactivated() {
        UUID achievementId = UUID.randomUUID();
        Achievement achievement = mock(Achievement.class);
        when(achievement.getId()).thenReturn(achievementId);
        when(achievement.getRuleJson()).thenReturn("{}");
        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(ruleCodec.decode(any())).thenReturn(null);

        // reactivate achievement
        service.setActive(achievementId, true);
        verify(auditLogger).recordAdmin(
            eq(AuditAction.ADMIN_ACHIEVEMENT_REACTIVATED),
            eq("ACHIEVEMENT"),
            eq(achievementId.toString()));
    }
}