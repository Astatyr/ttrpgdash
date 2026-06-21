package ttrpgdash.script;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and resolves standard dice notation strings.
 *
 * Supported formats:
 *   {@code 2d6}        — roll 2 six-sided dice
 *   {@code d20}        — roll 1 twenty-sided die
 *   {@code 1d4+2}      — roll, then add modifier
 *   {@code 3d8-1}      — roll, then subtract modifier
 *   {@code 5}          — fixed value, no roll
 */
public final class DiceRoller {

    private static final Random RANDOM = new Random();
    private static final Pattern DICE_PATTERN =
            Pattern.compile("(?i)^(\\d+)?d(\\d+)([+-]\\d+)?$");

    private DiceRoller() {}

    /**
     * Rolls the given dice notation and returns the total.
     *
     * @param notation a dice expression such as {@code "2d6+3"} or {@code "d20"}
     * @return the numeric result
     * @throws IllegalArgumentException if the notation cannot be parsed
     */
    public static int roll(String notation) {
        notation = notation.trim();

        if (notation.matches("\\d+")) {
            return Integer.parseInt(notation);
        }

        Matcher m = DICE_PATTERN.matcher(notation);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid dice notation: " + notation);
        }

        int count = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
        int sides = Integer.parseInt(m.group(2));
        int modifier = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;

        if (count < 1 || sides < 1) {
            throw new IllegalArgumentException("Dice count and sides must be >= 1: " + notation);
        }

        int total = 0;
        for (int i = 0; i < count; i++) {
            total += RANDOM.nextInt(sides) + 1;
        }
        return total + modifier;
    }

    /**
     * Returns a human-readable breakdown string without executing anything.
     * Example: {@code "2d6+3"} → {@code "2d6+3 (rolled: 4, 2) = 9"}
     */
    public static String describe(String notation) {
        notation = notation.trim();

        if (notation.matches("\\d+")) {
            return notation + " = " + notation;
        }

        Matcher m = DICE_PATTERN.matcher(notation);
        if (!m.matches()) {
            return notation + " [invalid notation]";
        }

        int count = m.group(1) != null ? Integer.parseInt(m.group(1)) : 1;
        int sides = Integer.parseInt(m.group(2));
        int modifier = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;

        int[] rolls = new int[count];
        int total = 0;
        for (int i = 0; i < count; i++) {
            rolls[i] = RANDOM.nextInt(sides) + 1;
            total += rolls[i];
        }
        total += modifier;

        StringBuilder sb = new StringBuilder(notation).append("  (");
        for (int i = 0; i < rolls.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(rolls[i]);
        }
        sb.append(")");
        if (modifier != 0) {
            sb.append(modifier > 0 ? " +" : " ").append(modifier);
        }
        sb.append("  =  ").append(total);
        return sb.toString();
    }
}
