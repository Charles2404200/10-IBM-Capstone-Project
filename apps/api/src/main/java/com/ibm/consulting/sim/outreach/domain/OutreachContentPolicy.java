package com.ibm.consulting.sim.outreach.domain;

import com.ibm.consulting.sim.ai.domain.OutreachEvaluationResult;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic quality guard around the model's qualitative assessment.
 *
 * <p>The model can interpret relevance and tone, but it must not be allowed to
 * reward abusive, generic or non-communicative input. This policy has the final
 * say on score caps and hard rejections; it is deliberately independent of any
 * provider so the learning rules stay stable across models.</p>
 */
public final class OutreachContentPolicy {
    private static final Pattern UNPROFESSIONAL = Pattern.compile(
            "\\b(fuck|fucking|shit|bullshit|wtf|idiot|moron|stupid|shut up|who tf)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED_CHARACTERS = Pattern.compile("(.)\\1{7,}");
    private static final Set<String> LOW_SIGNAL_MESSAGES = Set.of(
            "hi", "hello", "hey", "test", "asdf", "i dont know", "i don't know", "n/a", "no idea");

    private OutreachContentPolicy() { }

    public static OutreachEvaluationResult apply(OutreachEvaluationResult ai,
                                                   String subject,
                                                   String body,
                                                   String companyName,
                                                   String stakeholderName,
                                                   Collection<String> evidenceNotes) {
        String normalized = normalize(body);
        if (isUnprofessional(normalized)) {
            return new OutreachEvaluationResult(
                    "I am not prepared to continue this conversation in that tone. Please come back with a professional, client-focused message.",
                    "REJECTED", 0, 0, 0, 0, -15, -15);
        }

        int personalisation = blend(ai.personalisation(), personalisation(body, companyName, stakeholderName));
        int relevance = blend(ai.relevance(), relevance(body, evidenceNotes));
        int clarity = blend(ai.clarity(), clarity(subject, body));
        int callToAction = blend(ai.callToAction(), callToAction(body));

        if (isLowSignal(normalized)) {
            return new OutreachEvaluationResult(
                    "I am not clear on why you are contacting us or what you are asking for. Please send a concise message tied to a specific business priority.",
                    "FOLLOW_UP_REQUIRED", Math.min(personalisation, 20), Math.min(relevance, 20),
                    Math.min(clarity, 25), Math.min(callToAction, 20), -4, -3);
        }

        return new OutreachEvaluationResult(ai.clientReply(), ai.outcome(), personalisation, relevance,
                clarity, callToAction, ai.trustDelta(), ai.interestDelta());
    }

    static boolean isUnprofessional(String normalized) {
        return UNPROFESSIONAL.matcher(normalized).find();
    }

    private static boolean isLowSignal(String normalized) {
        String compact = normalized.replaceAll("\\s+", " ").trim();
        if (LOW_SIGNAL_MESSAGES.contains(compact) || REPEATED_CHARACTERS.matcher(compact).find()) return true;
        String[] words = compact.isBlank() ? new String[0] : compact.split(" ");
        if (words.length < 12) return true;
        long unique = java.util.Arrays.stream(words).distinct().count();
        return unique <= 3;
    }

    private static int personalisation(String body, String companyName, String stakeholderName) {
        String text = normalize(body);
        boolean companyMentioned = containsName(text, companyName);
        boolean stakeholderMentioned = containsName(text, stakeholderName);
        return stakeholderMentioned || companyMentioned ? 90 : 15;
    }

    private static int relevance(String body, Collection<String> evidenceNotes) {
        String text = normalize(body);
        long matched = evidenceNotes.stream()
                .filter(note -> note != null)
                .flatMap(note -> java.util.Arrays.stream(normalize(note).split("[^a-z0-9-]+")))
                .filter(token -> token.length() >= 5)
                .distinct()
                .filter(text::contains)
                .limit(3)
                .count();
        return matched >= 2 ? 90 : matched == 1 ? 60 : 15;
    }

    private static int clarity(String subject, String body) {
        int words = body.trim().isBlank() ? 0 : body.trim().split("\\s+").length;
        boolean hasSentences = body.split("[.!?]+").length >= 2;
        boolean shouty = body.chars().filter(Character::isLetter).count() > 12
                && body.chars().filter(Character::isLetter).allMatch(c -> !Character.isLowerCase(c));
        if (words < 35 || words > 220 || !hasSentences || shouty || subject.trim().length() < 5) return 20;
        return 90;
    }

    private static int callToAction(String body) {
        String text = normalize(body);
        boolean asks = text.contains("meeting") || text.contains("call") || text.contains("conversation") || text.contains("schedule");
        boolean concrete = text.matches(".*(\\d+|minute|next week|monday|tuesday|wednesday|thursday|friday).*" );
        long questions = body.chars().filter(c -> c == '?').count();
        return asks && concrete && questions <= 1 ? 90 : 15;
    }

    private static int blend(int modelScore, int deterministicScore) {
        return Math.max(0, Math.min(100, Math.round(modelScore * 0.6f + deterministicScore * 0.4f)));
    }

    private static boolean containsName(String text, String value) {
        if (value == null || value.isBlank()) return false;
        String normalizedValue = normalize(value);
        String firstWord = normalizedValue.split("\\s+")[0];
        return text.contains(normalizedValue) || (firstWord.length() >= 4 && text.contains(firstWord));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
