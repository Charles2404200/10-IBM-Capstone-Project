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
import java.util.UUID;

/**
 * Evaluates all active achievements against a learner's current progress and
 * unlocks any newly satisfied ones. Invoked synchronously right after an
 * assessment is generated (engagement completion is the natural point at which
 * a learner's fact sheet changes) — see {@code AssessmentService}.
 */
@Service
public class AchievementEvaluationService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementFactSheetBuilder factSheetBuilder;
    private final AchievementRuleCodec ruleCodec;
    private final AchievementRuleMapper ruleMapper;

    public AchievementEvaluationService(AchievementRepository achievementRepository,
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

    /**
     * Re-evaluates every active achievement for the given learner and unlocks any
     * that are newly satisfied. Returns the list of achievements unlocked by this
     * call (empty if none were newly unlocked), so callers may notify the learner.
     */
    @Transactional
    public List<Achievement> evaluateForUser(UUID userId) {
        AchievementFactSheet facts = factSheetBuilder.build(userId);
        List<Achievement> newlyUnlocked = new java.util.ArrayList<>();

        for (Achievement achievement : achievementRepository.findAllActive()) {
            if (userAchievementRepository.existsByUserIdAndAchievementId(userId, achievement.getId())) {
                continue;
            }
            AchievementCondition rule = ruleMapper.toDomain(ruleCodec.decode(achievement.getRuleJson()));
            if (AchievementRuleEvaluator.isSatisfied(rule, facts)) {
                userAchievementRepository.save(UserAchievement.unlock(userId, achievement.getId()));
                newlyUnlocked.add(achievement);
            }
        }
        return newlyUnlocked;
    }
}
