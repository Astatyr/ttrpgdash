package ttrpgdash.model;

/**
 * Represents a Player Character (PC).
 * Extends Entity with any PC-specific fields you want to add later
 * (e.g. class, spell slots, inspiration). Currently a clean subclass
 * so you can differentiate PCs from NPCs in the sidebar and serialisation.
 */
public class PlayerEntity extends Entity {

    /** Player's real name (optional, for DM reference). */
    private String playerName;

    public PlayerEntity(String id, String name, double sizeInFeet) {
        super(id, name, sizeInFeet);
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
    }

    @Override
    public String getEntityType() {
        return "player";
    }
}
