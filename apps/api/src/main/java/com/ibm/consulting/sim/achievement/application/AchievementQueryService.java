package com.ibm.consulting.sim.achievement.application;

import com.ibm.consulting.sim.achievement.domain.Achievement;
import com.ibm.consulting.sim.achievement.domain.AchievementCondition;
import com.ibm.consulting.sim.achievement.domain.AchievementFactSheet;
import com.ibm.consulting.sim.achievement.domain.AchievementRepository;
import com.ibm.consulting.sim.achievement.domain.AchievementRuleEvaluator;
import com.ibm.consulting.sim.achievement.domain.UserAchievement;
import com.ibm.consulting.sim.achievement.domain.UserAchievementRepository;
import com.ibm.consulting.sim.achievement.infrastructure.AchievementRuleCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Learner-facing read model: "my achievements" with unlock status and live progress. */
@Service
public class AchievementQueryService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementFactSheetBuilder factSheetBuilder;
    private final AchievementRuleCodec ruleCodec;
    private final AchievementRuleMapper ruleMapper;

    public AchievementQueryService(AchievementRepository achievementRepository,
                                    UserAchievementRepository userAchievementRepository,
                                    AchievementFactSheetBuilder factSheetBuilder,
                                    AchievementRuleCodec ruleCodec,
                                    AchievementRuleMapper ruleMapper) {
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.factSheetBuilder = factSheetBuilder;
        this.ruleCodec = ruleCodec;
        this.ruleMapper = ruleMapper;
    }

    @Transactional(readOnly = true)
    public List<AchievementSummary> listForUser(UUID userId) {
        List<UserAchievement> unlocks = userAchievementRepository.findByUserId(userId);
        Map<UUID, UserAchievement> unlockByAchievement = unlocks.stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementId, ua -> ua));

        AchievementFactSheet facts = factSheetBuilder.build(userId);

        return achievementRepository.findAllActive().stream()
                .map(achievement -> {
                    UserAchievement unlock = unlockByAchievement.get(achievement.getId());
                    if (unlock != null) {
                        return AchievementSummary.unlocked(achievement, unlock.getUnlockedAt());
                    }
                    AchievementCondition rule = ruleMapper.toDomain(ruleCodec.decode(achievement.getRuleJson()));
                    double progress = AchievementRuleEvaluator.progress(rule, facts);
                    return AchievementSummary.locked(achievement, progress);
                })
                .toList();
    }
}
