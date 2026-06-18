package ttrpgdash.entity;

import java.util.List;
import java.util.Set;

/**
 * Constants and validation for status effects that can be applied to entities.
 */
public final class StatusEffect {

    public static final List<String> ALL = List.of(
            "poisoned",
            "stunned",
            "burning",
            "frozen",
            "bleeding",
            "cursed",
            "invisible",
            "blessed",
            "exhausted"
    );

    private static final Set<String> VALID = Set.copyOf(ALL);

    private StatusEffect() {}

    public static boolean isValid(String effect) {
        return VALID.contains(effect);
    }

    public static String iconPath(String effect) {
        return "assets/statuseffects/" + effect + ".png";
    }
}
