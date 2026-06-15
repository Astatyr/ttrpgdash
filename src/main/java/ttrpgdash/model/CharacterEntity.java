package ttrpgdash.model;

/**
 * Represents any non-player entity: NPCs, enemies, creatures, bosses,
 * summons, vehicles — anything the DM controls. Named "Character" because
 * the boundary between enemy and character is fluid in many TTRPGs.
 *
 * Extends Entity with NPC-specific fields. Add creature type, CR,
 * faction, or anything else here as your campaign grows.
 */
public class CharacterEntity extends Entity {

    // ── NPC-specific fields (expand as needed) ────────────────────────────────

    /**
     * Optional label for grouping (e.g. "Goblin", "Dragon", "Merchant").
     * Used for display in the sidebar — not mechanically enforced.
     */
    private String creatureType;

    /**
     * Whether this entity is currently hostile to the party.
     * Can be toggled mid-session (e.g. an NPC that turns on the players).
     */
    private boolean hostile;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CharacterEntity(String id, String name, double sizeInFeet) {
        super(id, name, sizeInFeet);
        this.hostile = true; // default assumption for non-player entities
    }

    // ── Getters & setters ─────────────────────────────────────────────────────

    public String getCreatureType()                  { return creatureType; }
    public void   setCreatureType(String type)       { this.creatureType = type; }

    public boolean isHostile()                       { return hostile; }
    public void    setHostile(boolean hostile)       { this.hostile = hostile; }

    // ── Type tag ──────────────────────────────────────────────────────────────

    @Override
    public String getEntityType() { return "character"; }
}
