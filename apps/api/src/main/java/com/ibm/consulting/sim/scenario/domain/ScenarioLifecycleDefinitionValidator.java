package com.ibm.consulting.sim.scenario.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates authored lifecycle structure, dependency order, condition shape, and condition availability. */
public final class ScenarioLifecycleDefinitionValidator {

    public static final int MAX_STAGES = 20;
    public static final int MAX_OBJECTIVES = 50;
    public static final int MAX_CONDITION_DEPTH = 4;
    public static final int MAX_CONDITION_CHILDREN = 10;
    public static final int MAX_CONDITION_NODES = 100;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_LABEL_LENGTH = 100;
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_TEXT_LENGTH = 2_000;

    private static final Pattern STABLE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Set<LifecycleConditionType> PERCENTAGE_CONDITIONS = EnumSet.of(
            LifecycleConditionType.MIN_RESEARCH_CONFIDENCE,
            LifecycleConditionType.MIN_PREPARATION_SCORE,
            LifecycleConditionType.MIN_TRUST,
            LifecycleConditionType.MIN_INTEREST,
            LifecycleConditionType.MIN_PATIENCE);

    /** Throws one explicit domain exception containing every validation error. */
    public void validate(ScenarioLifecycleDefinition definition) {
        List<String> errors = errors(definition);
        if (!errors.isEmpty()) {
            throw new InvalidScenarioLifecycleDefinitionException(
                    "Invalid scenario lifecycle definition: " + String.join("; ", errors));
        }
    }

    /** Returns all authoring-readiness errors without throwing, suitable for an admin UI or publish checks. */
    public List<String> errors(ScenarioLifecycleDefinition definition) {
        if (definition == null) return List.of("Lifecycle definition is required");

        List<String> errors = new ArrayList<>();
        if (definition.schemaVersion() != ScenarioLifecycleDefinition.CURRENT_SCHEMA_VERSION) {
            errors.add("Unsupported lifecycle schema version " + definition.schemaVersion());
        }

        List<ScenarioStageDefinition> allStages = definition.stages();
        List<ScenarioObjectiveDefinition> allObjectives = definition.objectives();
        if (allStages.size() > MAX_STAGES) errors.add("Lifecycle cannot contain more than 20 stages");
        if (allObjectives.size() > MAX_OBJECTIVES) errors.add("Lifecycle cannot contain more than 50 objectives");
        List<ScenarioStageDefinition> stages = bounded(allStages, MAX_STAGES);
        List<ScenarioObjectiveDefinition> objectives = bounded(allObjectives, MAX_OBJECTIVES);

        validateStages(stages, errors);
        validateObjectives(stages, objectives, errors);

        NodeCounter conditionNodes = new NodeCounter();
        for (ScenarioStageDefinition stage : stages) {
            if (stage == null) continue;
            validateCondition(stage.entryCondition(),
                    "Stage " + displayKey(stage.key()) + " entry condition", 1, conditionNodes, errors);
            validateCondition(stage.completionCondition(),
                    "Stage " + displayKey(stage.key()) + " completion condition", 1, conditionNodes, errors);
            validateStageConditionAvailability(stage, stage.entryCondition(), true, errors);
            validateStageConditionAvailability(stage, stage.completionCondition(), false, errors);
        }
        Map<String, ScenarioStageDefinition> stagesByKey = stagesByKey(stages);
        for (ScenarioObjectiveDefinition objective : objectives) {
            if (objective == null) continue;
            validateCondition(objective.completionCondition(),
                    "Objective " + displayKey(objective.key()) + " completion condition", 1, conditionNodes, errors);
            validateObjectiveConditionAvailability(objective, stagesByKey.get(objective.stageKey()), errors);
        }
        if (conditionNodes.count > MAX_CONDITION_NODES) {
            errors.add("Lifecycle cannot contain more than 100 condition nodes");
        }
        return List.copyOf(errors);
    }

    private static void validateStages(List<ScenarioStageDefinition> stages, List<String> errors) {
        Set<String> keys = new HashSet<>();
        Set<StageCapability> capabilities = EnumSet.noneOf(StageCapability.class);
        Set<Integer> orders = new HashSet<>();
        for (ScenarioStageDefinition stage : stages) {
            if (stage == null) {
                errors.add("Lifecycle stages cannot contain null entries");
                continue;
            }
            validateKey(stage.key(), "stage key", errors);
            if (stage.key() != null && !keys.add(stage.key())) errors.add("Duplicate stage key " + stage.key());
            if (stage.capability() == null) {
                errors.add("Stage " + displayKey(stage.key()) + " capability is required");
            } else if (!capabilities.add(stage.capability())) {
                errors.add("Duplicate stage capability " + stage.capability());
            }
            if (!orders.add(stage.displayOrder())) errors.add("Duplicate stage display order " + stage.displayOrder());
            if (stage.displayOrder() < 0) errors.add("Stage display order must be nonnegative");
            validateRequiredText(stage.label(), MAX_LABEL_LENGTH, "stage label", errors);
            validateOptionalText(stage.description(), "Stage description", errors);
            validateOptionalText(stage.goal(), "Stage goal", errors);
            validateOptionalText(stage.doneText(), "Stage done text", errors);
            validateOptionalText(stage.nextText(), "Stage next text", errors);
            if (!stage.required() && stage.capability() != StageCapability.MEETING_REVIEW) {
                errors.add("Only MEETING_REVIEW may be optional while all technical capabilities remain present");
            }
        }

        for (StageCapability capability : StageCapability.values()) {
            if (!capabilities.contains(capability)) errors.add("Lifecycle must contain capability " + capability);
        }

        List<ScenarioStageDefinition> ordered = stages.stream()
                .filter(stage -> stage != null && stage.capability() != null)
                .sorted(java.util.Comparator.comparingInt(ScenarioStageDefinition::displayOrder))
                .toList();
        if (!ordered.isEmpty() && ordered.get(0).capability() != StageCapability.LEAD) {
            errors.add("LEAD must be the start stage");
        }
        if (!ordered.isEmpty() && ordered.get(ordered.size() - 1).capability() != StageCapability.COMPLETED) {
            errors.add("COMPLETED must be the terminal stage");
        }
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i - 1).capability().ordinal() >= ordered.get(i).capability().ordinal()) {
                errors.add("Stages must follow technical dependency order");
                break;
            }
        }
    }

    private static void validateObjectives(
            List<ScenarioStageDefinition> stages,
            List<ScenarioObjectiveDefinition> objectives,
            List<String> errors) {
        Set<String> stageKeys = stagesByKey(stages).keySet();
        Set<String> objectiveKeys = new HashSet<>();
        Set<Integer> objectiveOrders = new HashSet<>();
        Map<String, ScenarioObjectiveDefinition> objectivesByKey = new HashMap<>();

        for (ScenarioObjectiveDefinition objective : objectives) {
            if (objective == null) {
                errors.add("Lifecycle objectives cannot contain null entries");
                continue;
            }
            validateKey(objective.key(), "Objective key", errors);
            if (objective.key() != null && !objectiveKeys.add(objective.key())) {
                errors.add("Duplicate objective key " + objective.key());
            } else if (objective.key() != null) {
                objectivesByKey.put(objective.key(), objective);
            }
            if (!objectiveOrders.add(objective.displayOrder())) {
                errors.add("Duplicate objective display order " + objective.displayOrder());
            }
            if (objective.displayOrder() < 0) errors.add("Objective display order must be nonnegative");
            if (objective.stageKey() != null && !stageKeys.contains(objective.stageKey())) {
                errors.add("Objective " + displayKey(objective.key()) + " refers to unknown stage "
                        + displayKey(objective.stageKey()));
            }
            validateRequiredText(objective.title(), MAX_TITLE_LENGTH, "Objective title", errors);
            validateOptionalText(objective.description(), "Objective description", errors);
        }

        for (ScenarioObjectiveDefinition objective : objectives) {
            if (objective == null || objective.parentObjectiveKey() == null) continue;
            ScenarioObjectiveDefinition parent = objectivesByKey.get(objective.parentObjectiveKey());
            if (parent == null) {
                errors.add("Objective " + displayKey(objective.key()) + " refers to unknown parent "
                        + objective.parentObjectiveKey());
            } else if (objective.required()
                    && objectiveStageOrdinal(parent, stages) < objectiveStageOrdinal(objective, stages)) {
                errors.add("Objective " + displayKey(objective.key())
                        + " is a required child scheduled after parent " + parent.key());
            }
        }
        detectObjectiveCycles(objectivesByKey, errors);
    }

    private static void detectObjectiveCycles(
            Map<String, ScenarioObjectiveDefinition> objectivesByKey, List<String> errors) {
        Set<String> fullyVisited = new HashSet<>();
        for (String start : objectivesByKey.keySet()) {
            if (fullyVisited.contains(start)) continue;
            Set<String> path = new HashSet<>();
            String current = start;
            while (current != null && objectivesByKey.containsKey(current) && !fullyVisited.contains(current)) {
                if (!path.add(current)) {
                    errors.add("Objective parent tree contains a cycle at " + current);
                    break;
                }
                current = objectivesByKey.get(current).parentObjectiveKey();
            }
            fullyVisited.addAll(path);
        }
    }

    private static void validateCondition(
            LifecycleConditionNode node, String path, int depth, NodeCounter counter, List<String> errors) {
        if (node == null || counter.count > MAX_CONDITION_NODES) return;
        counter.count++;
        if (depth > MAX_CONDITION_DEPTH) {
            errors.add(path + " exceeds maximum condition depth 4");
            return;
        }
        if (node.kind() == null) {
            errors.add(path + " kind is required");
            return;
        }
        if (node.kind() == LifecycleConditionNode.Kind.GROUP) {
            if (node.operator() == null || node.children() == null || node.children().isEmpty()
                    || node.conditionType() != null || node.threshold() != null || node.value() != null) {
                errors.add(path + " has malformed GROUP shape");
            }
            if (node.children() != null) {
                if (node.children().size() > MAX_CONDITION_CHILDREN) {
                    errors.add(path + " cannot contain more than 10 children");
                }
                int childLimit = Math.min(node.children().size(), MAX_CONDITION_CHILDREN);
                for (int i = 0; i < childLimit; i++) {
                    LifecycleConditionNode child = node.children().get(i);
                    if (child == null) {
                        errors.add(path + " contains a null child");
                    } else {
                        validateCondition(child, path + ".children[" + i + "]", depth + 1, counter, errors);
                    }
                }
            }
        } else {
            if (node.operator() != null || node.children() != null) errors.add(path + " has malformed LEAF shape");
            if (node.conditionType() == null) {
                errors.add(path + " condition type is required");
            } else {
                validateLeafValues(node, path, errors);
            }
        }
    }

    private static void validateLeafValues(LifecycleConditionNode node, String path, List<String> errors) {
        Double threshold = node.threshold();
        if (node.conditionType() == LifecycleConditionType.MIN_EVIDENCE_COUNT) {
            if (!finiteBetween(threshold, 0, 1_000)) {
                errors.add(path + " evidence threshold must be between 0 and 1000");
            } else if (threshold != Math.rint(threshold)) {
                errors.add(path + " evidence threshold must be an integer");
            }
        } else if (PERCENTAGE_CONDITIONS.contains(node.conditionType())) {
            if (!finiteBetween(threshold, 0, 100)) {
                errors.add(path + " score threshold must be between 0 and 100");
            }
        } else if (threshold != null) {
            errors.add(path + " threshold is not supported for " + node.conditionType());
        }
        if (node.value() != null) errors.add(path + " value is not supported for " + node.conditionType());
    }

    private static boolean finiteBetween(Double value, double minimum, double maximum) {
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private static void validateStageConditionAvailability(
            ScenarioStageDefinition stage,
            LifecycleConditionNode condition,
            boolean entry,
            List<String> errors) {
        if (stage.capability() == null || condition == null) return;
        for (LifecycleConditionType type : leafTypes(condition)) {
            if (type == LifecycleConditionType.CURRENT_STAGE_COMPLETED) {
                errors.add("CURRENT_STAGE_COMPLETED is allowed for objectives, not stage "
                        + (entry ? "entry" : "completion") + " conditions");
                continue;
            }
            type.availableFrom().ifPresent(availableFrom -> {
                boolean unavailable = entry
                        ? availableFrom.ordinal() > stage.capability().ordinal()
                            || (availableFrom == stage.capability() && !type.availableAtStageEntry())
                        : availableFrom.ordinal() > stage.capability().ordinal();
                if (unavailable) {
                    errors.add(type + " is not available for " + stage.capability() + " "
                            + (entry ? "entry" : "completion") + " condition");
                }
            });
        }
    }

    private static void validateObjectiveConditionAvailability(
            ScenarioObjectiveDefinition objective,
            ScenarioStageDefinition stage,
            List<String> errors) {
        if (objective.completionCondition() == null) return;
        StageCapability objectiveCapability = stage == null || stage.capability() == null
                ? StageCapability.COMPLETED : stage.capability();
        for (LifecycleConditionType type : leafTypes(objective.completionCondition())) {
            type.availableFrom().ifPresent(availableFrom -> {
                if (availableFrom.ordinal() > objectiveCapability.ordinal()) {
                    errors.add(type + " is not available for objective " + displayKey(objective.key())
                            + " at stage " + objectiveCapability);
                }
            });
        }
    }

    private static List<LifecycleConditionType> leafTypes(LifecycleConditionNode root) {
        List<LifecycleConditionType> types = new ArrayList<>();
        collectLeafTypes(root, types, 1);
        return types;
    }

    private static void collectLeafTypes(
            LifecycleConditionNode node, List<LifecycleConditionType> types, int depth) {
        if (node == null || depth > MAX_CONDITION_DEPTH + 1) return;
        if (node.kind() == LifecycleConditionNode.Kind.LEAF && node.conditionType() != null) {
            types.add(node.conditionType());
        } else if (node.children() != null) {
            int childLimit = Math.min(node.children().size(), MAX_CONDITION_CHILDREN);
            for (int i = 0; i < childLimit; i++) collectLeafTypes(node.children().get(i), types, depth + 1);
        }
    }

    private static Map<String, ScenarioStageDefinition> stagesByKey(List<ScenarioStageDefinition> stages) {
        Map<String, ScenarioStageDefinition> result = new HashMap<>();
        for (ScenarioStageDefinition stage : stages) {
            if (stage != null && stage.key() != null) result.putIfAbsent(stage.key(), stage);
        }
        return result;
    }

    private static void validateKey(String key, String field, List<String> errors) {
        if (key == null || !STABLE_KEY.matcher(key).matches()) {
            errors.add(field + " must be an uppercase identifier of at most 64 characters");
        }
    }

    private static void validateRequiredText(String value, int maximum, String field, List<String> errors) {
        if (value == null || value.isBlank()) errors.add(field + " is required");
        else if (value.length() > maximum) errors.add(field + " cannot exceed " + maximum + " characters");
    }

    private static void validateOptionalText(String value, String field, List<String> errors) {
        if (value != null && value.length() > MAX_TEXT_LENGTH) {
            errors.add(field + " cannot exceed 2000 characters");
        }
    }

    private static String displayKey(String key) {
        return key == null ? "<missing>" : key;
    }

    private static int objectiveStageOrdinal(
            ScenarioObjectiveDefinition objective, List<ScenarioStageDefinition> stages) {
        if (objective.stageKey() == null) return StageCapability.COMPLETED.ordinal();
        ScenarioStageDefinition stage = stagesByKey(stages).get(objective.stageKey());
        return stage == null || stage.capability() == null ? Integer.MAX_VALUE : stage.capability().ordinal();
    }

    private static <T> List<T> bounded(List<T> values, int maximum) {
        return values.size() <= maximum ? values : values.subList(0, maximum);
    }

    private static final class NodeCounter {
        private int count;
    }
}
