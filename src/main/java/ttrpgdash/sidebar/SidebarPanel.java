package ttrpgdash.sidebar;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ttrpgdash.model.CharacterEntity;
import ttrpgdash.model.Entity;
import ttrpgdash.model.GameState;
import ttrpgdash.model.PlayerEntity;
import ttrpgdash.util.FileHelper;

/**
 * The sidebar panel on the left of the main window.
 *
 * Contains:
 *   - "Players" section with entity cards for each PlayerEntity
 *   - "Characters" section with entity cards for each CharacterEntity
 *   - Add buttons for each section that open a folder browser dialog
 *
 * Callbacks injected by MainWindow:
 *   - onPlaceEntity:   user pressed "Place" on a card
 *   - onDetailsEntity: user pressed "Details" on a card
 *   - onEntitiesChanged: called after add/delete so MainWindow can sync the map
 */
public class SidebarPanel extends VBox {

    private final GameState gameState;
    private Stage ownerStage;

    private Consumer<Entity> onPlaceEntity;
    private Consumer<Entity> onDetailsEntity;
    private Runnable onEntitiesChanged;

    private EntityCard armedCard = null;

    private final VBox playersList = new VBox();
    private final VBox charactersList = new VBox();

    /**
     * Creates the sidebar panel bound to the given game state.
     */
    public SidebarPanel(GameState gameState) {
        this.gameState = gameState;
        buildUI();
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    private void buildUI() {
        setStyle("-fx-background-color: #12121e;");
        setSpacing(0);
        setPrefWidth(240);
        setMinWidth(200);
        setMaxWidth(300);

        Label header = new Label("ENTITIES");
        header.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-font-weight: bold; "
                + "-fx-padding: 12 8 6 8; -fx-letter-spacing: 1.5px;");

        VBox playersSection = buildSection("Players", "#6fa8dc", playersList, this::addPlayer);

        VBox charsSection = buildSection("Characters / NPCs", "#e06c75", charactersList,
                this::addCharacter);

        // Scroll wraps both sections
        VBox content = new VBox(playersSection, charsSection);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle(
                "-fx-background: #12121e; -fx-background-color: #12121e; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
        refresh();
    }

    private VBox buildSection(String title, String color, VBox list, Runnable addAction) {
        Label sectionLabel = new Label(title);
        sectionLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; "
                + "-fx-font-weight: bold; -fx-padding: 8 8 4 8;");

        Button addBtn = new Button("+ Add");
        addBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + color
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-border-color: " + color
                + "; -fx-border-radius: 4; -fx-padding: 2 8 2 8;");
        addBtn.setOnAction(e -> addAction.run());

        HBox sectionHeader = new HBox(sectionLabel, addBtn);
        HBox.setHgrow(sectionLabel, Priority.ALWAYS);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);
        sectionHeader.setPadding(new Insets(0, 8, 4, 0));

        list.setSpacing(0);

        VBox section = new VBox(sectionHeader, list);
        section.setPadding(new Insets(0, 0, 8, 0));
        return section;
    }

    /**
     * Rebuilds all entity cards from the current GameState.
     * Call this after adding, removing, or modifying entities.
     */
    public void refresh() {
        playersList.getChildren().clear();
        for (PlayerEntity p : gameState.getPlayers()) {
            playersList.getChildren().add(makeCard(p));
        }

        charactersList.getChildren().clear();
        for (CharacterEntity c : gameState.getCharacters()) {
            charactersList.getChildren().add(makeCard(c));
        }
    }

    private EntityCard makeCard(Entity entity) {
        EntityCard card = new EntityCard(entity);

        card.setOnPlace(() -> {
            // Disarm previous card
            if (armedCard != null) {
                armedCard.setArmed(false);
            }
            armedCard = card;
            card.setArmed(true);
            if (onPlaceEntity != null) {
                onPlaceEntity.accept(entity);
            }
        });

        card.setOnDetails(e -> {
            if (onDetailsEntity != null) {
                onDetailsEntity.accept(e);
            }
        });

        card.setOnDelete(e -> {
            // Remove from GameState
            if (e instanceof PlayerEntity) {
                gameState.removePlayer(e.getId());
            }
            if (e instanceof CharacterEntity) {
                gameState.removeCharacter(e.getId());
            }
            refresh();
            if (onEntitiesChanged != null) {
                onEntitiesChanged.run();
            }
        });

        return card;
    }

    private void addPlayer() {
        File folder = FileHelper.browseForCharacterFolder(ownerStage);
        if (folder == null) {
            return;
        }

        String name = assignDisplayName(folder.getName(), gameState.getPlayers());
        String id = FileHelper.generateId(name);

        PlayerEntity player = new PlayerEntity(id, name, 5.0);
        player.setAvatarPath(resolveAsset(folder, "Avatar.png"));
        player.setDetailsPath(resolveAsset(folder, "Details.png"));

        double size = promptForSize(name, 5.0);
        player.setSizeInFeet(size);

        gameState.addPlayer(player);
        refresh();
        if (onEntitiesChanged != null) {
            onEntitiesChanged.run();
        }
    }

    private void addCharacter() {
        File folder = FileHelper.browseForCharacterFolder(ownerStage);
        if (folder == null) {
            return;
        }

        String name = assignDisplayName(folder.getName(), gameState.getCharacters());
        String id = FileHelper.generateId(name);

        CharacterEntity character = new CharacterEntity(id, name, 5.0);
        character.setAvatarPath(resolveAsset(folder, "Avatar.png"));
        character.setDetailsPath(resolveAsset(folder, "Details.png"));

        double size = promptForSize(name, 5.0);
        character.setSizeInFeet(size);

        gameState.addCharacter(character);
        refresh();
        if (onEntitiesChanged != null) {
            onEntitiesChanged.run();
        }
    }

    /**
     * Returns the lowest available display name for the given base name within a list.
     * The first slot uses the bare base name; subsequent slots append " 2", " 3", etc.
     * Gaps left by removals are filled before creating a new highest number.
     */
    private String assignDisplayName(String baseName, List<? extends Entity> existing) {
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

    /**
     * Checks if Avatar.png or Details.png exists in the chosen folder.
     * Returns the absolute path if found, null otherwise.
     */
    private String resolveAsset(File folder, String filename) {
        File f = new File(folder, filename);
        return f.exists() ? f.getAbsolutePath() : null;
    }

    /**
     * Shows a small dialog asking the DM for this entity's size in feet.
     * Returns the entered value, or the default if cancelled.
     */
    private double promptForSize(String entityName, double defaultSize) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf((int) defaultSize));
        dialog.setTitle("Entity Size");
        dialog.setHeaderText("Size for: " + entityName);
        dialog.setContentText("Diameter in feet (e.g. 5 = medium, 10 = large):");
        return dialog.showAndWait()
                .map(s -> {
                    try {
                        return Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        return defaultSize;
                    }
                })
                .orElse(defaultSize);
    }

    /** Disarms the currently armed card (after placement or cancellation). */
    public void disarmCard() {
        if (armedCard != null) {
            armedCard.setArmed(false);
            armedCard = null;
        }
    }

    public void setOnPlaceEntity(Consumer<Entity> handler) {
        this.onPlaceEntity = handler;
    }

    public void setOnDetailsEntity(Consumer<Entity> handler) {
        this.onDetailsEntity = handler;
    }

    public void setOnEntitiesChanged(Runnable handler) {
        this.onEntitiesChanged = handler;
    }
}
