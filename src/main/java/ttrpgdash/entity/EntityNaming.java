package ttrpgdash.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for assigning unique display names to entities within a session.
 * The first slot uses the bare base name; subsequent slots append " 2", " 3", etc.
 * Gaps left by removals are filled before creating a new highest number.
 */
final class EntityNaming {

    private EntityNaming() {}

    /**
     * Returns the lowest available display name for the given base name within a list.
     */
    static String assignDisplayName(String baseName, List<? extends Entity> existing) {
        Set<Integer> taken = new HashSet<>();
        for (Entity e : existing) {
            if (e.getName().equals(baseName)) {
                taken.add(1);
            } else if (e.getName().startsWith(baseName + " ")) {
                String suffix = e.getName().substring(baseName.length() + 1);
                try {
                    int n = Integer.parseInt(suffix);
                    if (n >= 2) {
                        taken.add(n);
                    }
                } catch (NumberFormatException ignored) {
                    // Name shares the prefix but is not a numbered duplicate — skip
                }
            }
        }
        if (!taken.contains(1)) {
            return baseName;
        }
        int n = 2;
        while (taken.contains(n)) {
            n++;
        }
        return baseName + " " + n;
    }
}
