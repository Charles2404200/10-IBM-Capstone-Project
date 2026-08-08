package com.ibm.consulting.sim.achievement.domain;

import com.ibm.consulting.sim.shared.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * An admin-authored achievement definition. The unlock rule itself is stored as
 * an opaque serialised string ({@code ruleJson}) — encoding/decoding it into an
 * {@link AchievementCondition} tree is an infrastructure concern (Jackson), kept
 * out of this domain entity by design.
 */
@Entity
@Table(name = "achievements")
public class Achievement extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "rule_json", nullable = false, columnDefinition = "text")
    private String ruleJson;

    protected Achievement() {}

    public static Achievement create(String name, String description, String iconKey, String ruleJson) {
        Achievement achievement = new Achievement();
        achievement.name = name;
        achievement.description = description;
        achievement.iconKey = (iconKey == null || iconKey.isBlank()) ? "trophy" : iconKey;
        achievement.ruleJson = ruleJson;
        achievement.active = true;
        return achievement;
    }

    public void updateRule(String ruleJson) {
        this.ruleJson = ruleJson;
    }

    public void updateDetails(String name, String description, String iconKey) {
        this.name = name;
        this.description = description;
        this.iconKey = (iconKey == null || iconKey.isBlank()) ? "trophy" : iconKey;
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconKey() { return iconKey; }
    public boolean isActive() { return active; }
    public String getRuleJson() { return ruleJson; }
}
