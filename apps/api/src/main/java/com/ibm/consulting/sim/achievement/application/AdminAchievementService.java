package com.ibm.consulting.sim.achievement.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ibm.consulting.sim.achievement.domain.Achievement;
import com.ibm.consulting.sim.achievement.domain.AchievementRepository;
import com.ibm.consulting.sim.achievement.infrastructure.AchievementRuleCodec;
import com.ibm.consulting.sim.shared.domain.NotFoundException;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditAction;
import com.ibm.consulting.sim.shared.infrastructure.observability.AuditLogger;

/**
 * Administrative CRUD for achievement definitions (create/update rule tree,
 * activate/deactivate). Restricted at the controller layer to {@code ADMINISTRATOR}
 * since achievements are a platform-wide progression concept, not scenario-scoped
 * content (unlike {@code ScenarioService}, which authors also manage).
 */
@Service
public class AdminAchievementService {

    private final AchievementRepository achievementRepository;
    private final AchievementRuleMapper ruleMapper;
    private final AchievementRuleCodec ruleCodec;
    private final AuditLogger auditLogger;

    public AdminAchievementService(AchievementRepository achievementRepository,
                                    AchievementRuleMapper ruleMapper,
                                    AchievementRuleCodec ruleCodec,
                                    AuditLogger auditLogger) {
        this.achievementRepository = achievementRepository;
        this.ruleMapper = ruleMapper;
        this.ruleCodec = ruleCodec;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<AchievementAdminView> listAll() {
        return achievementRepository.findAll().stream().map(this::toAdminView).toList();
    }

    @Transactional
    public AchievementAdminView create(UpsertAchievementRequest request) {
        // Validate the rule tree is well-formed (domain constructors enforce invariants) before persisting.
        ruleMapper.toDomain(request.rule());
        String ruleJson = ruleCodec.encode(request.rule());
        Achievement achievement = Achievement.create(request.name(), request.description(), request.iconKey(), ruleJson);
        achievementRepository.save(achievement);
        auditLogger.recordAdmin(AuditAction.ADMIN_ACHIEVEMENT_CREATED, "ACHIEVEMENT", achievement.getId().toString());
        return toAdminView(achievement);
    }

    @Transactional
    public AchievementAdminView update(UUID id, UpsertAchievementRequest request) {
        Achievement achievement = findAchievement(id);
        ruleMapper.toDomain(request.rule());
        achievement.updateDetails(request.name(), request.description(), request.iconKey());
        achievement.updateRule(ruleCodec.encode(request.rule()));
        achievementRepository.save(achievement);
        auditLogger.recordAdmin(AuditAction.ADMIN_ACHIEVEMENT_UPDATED, "ACHIEVEMENT", achievement.getId().toString());
        return toAdminView(achievement);
    }

    @Transactional
    public AchievementAdminView setActive(UUID id, boolean active) {
        Achievement achievement = findAchievement(id);
        if (active) {
            achievement.activate();
        } else {
            achievement.deactivate();
        }
        achievementRepository.save(achievement);
        auditLogger.recordAdmin(active ? AuditAction.ADMIN_ACHIEVEMENT_REACTIVATED : AuditAction.ADMIN_ACHIEVEMENT_DEACTIVATED, "ACHIEVEMENT", achievement.getId().toString());
        return toAdminView(achievement);
    }

    private Achievement findAchievement(UUID id) {
        return achievementRepository.findById(id).orElseThrow(() -> new NotFoundException("Achievement", id));
    }

    private AchievementAdminView toAdminView(Achievement achievement) {
        return AchievementAdminView.from(achievement, ruleCodec.decode(achievement.getRuleJson()));
    }
}
