package ttrpgdash.scene;

/**
 * Lightweight scene metadata stored in the master scenes.json.
 * The full scene data (entities, map, music) lives in its own file.
 */
public class SceneEntry {

    private String id;
    private String name;
    private int order;

    /**
     * Creates a scene entry with the given id, display name, and sort order.
     */
    public SceneEntry(String id, String name, int order) {
        this.id = id;
        this.name = name;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
