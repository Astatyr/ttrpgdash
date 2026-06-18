package ttrpgdash.entity;

/**
 * Represents any non-player entity: NPCs, enemies, creatures, bosses,
 * summons, vehicles — anything the DM controls. Named "Character" because
 * the boundary between enemy and character is fluid in many TTRPGs.
 *
 * Extends Entity with NPC-specific fields. Add creature type, CR,
 * faction, or anything else here as your campaign grows.
 */
public class CharacterEntity extends Entity {

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

    /**
     * Creates a new character entity with the given ID, name, and size.
     * Defaults to hostile since most non-player entities are adversaries.
     */
    public CharacterEntity(String id, String name, double sizeInFeet) {
        super(id, name, sizeInFeet);
        this.hostile = true;
    }

    public String getCreatureType() {
        return creatureType;
    }

    public void setCreatureType(String type) {
        this.creatureType = type;
    }

    public boolean isHostile() {
        return hostile;
    }

    public void setHostile(boolean hostile) {
        this.hostile = hostile;
    }

    @Override
    public String getEntityType() {
        return "character";
    }
}
