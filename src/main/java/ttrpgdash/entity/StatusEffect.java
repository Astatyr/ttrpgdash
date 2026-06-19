package ttrpgdash.entity;

import java.util.List;
import java.util.Set;

import javafx.scene.paint.Color;

/**
 * Constants and metadata for status effects that can be applied to entities.
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

    /**
     * Returns a fallback color for a given status effect, in case the icon is missing.
     *
     * @param effect the status effect name
     * @return a Color object representing the fallback color
     */
    public static Color fallbackColor(String effect) {
        return switch (effect.toLowerCase()) {
        case "poisoned" -> Color.LIMEGREEN;
        case "stunned" -> Color.ORANGE;
        case "burning" -> Color.ORANGERED;
        case "frozen" -> Color.DEEPSKYBLUE;
        case "bleeding" -> Color.CRIMSON;
        case "cursed" -> Color.MEDIUMPURPLE;
        case "invisible" -> Color.LIGHTGRAY;
        case "blessed" -> Color.GOLD;
        case "exhausted" -> Color.SADDLEBROWN;
        default -> Color.WHITE;
        };
    }
}
