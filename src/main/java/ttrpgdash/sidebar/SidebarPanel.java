package ttrpgdash.sidebar;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ttrpgdash.model.*;
import ttrpgdash.util.FileHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    // ── State ─────────────────────────────────────────────────────────────────

    private final GameState gameState;
    private Stage           ownerStage;

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private Consumer<Entity> onPlaceEntity;
    private Consumer<Entity> onDetailsEntity;
    private Runnable         onEntitiesChanged;

    // ── Armed card (highlighted for placement) ────────────────────────────────

    private EntityCard armedCard = null;

    // ── UI sections ───────────────────────────────────────────────────────────

    private final VBox playersList     = new VBox();
    private final VBox charactersList  = new VBox();

    // ── Constructor ───────────────────────────────────────────────────────────

    public SidebarPanel(GameState gameState) {
        this.gameState = gameState;
        buildUI();
    }

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }

    // ── UI build ──────────────────────────────────────────────────────────────

    private void buildUI() {
        setStyle("-fx-background-color: #12121e;");
        setSpacing(0);
        setPrefWidth(240);
        setMinWidth(200);
        setMaxWidth(300);

        // ── Header ────────────────────────────────────────────────────────────
        Label header = new Label("ENTITIES");
        header.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-font-weight: bold; "
                + "-fx-padding: 12 8 6 8; -fx-letter-spacing: 1.5px;");

        // ── Players section ───────────────────────────────────────────────────
        VBox playersSection = buildSection("Players", "#6fa8dc", playersList, this::addPlayer);

        // ── Characters section ────────────────────────────────────────────────
        VBox charsSection   = buildSection("Characters / NPCs", "#e06c75", charactersList, this::addCharacter);

        // Scroll wraps both sections
        VBox content = new VBox(playersSection, charsSection);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #12121e; -fx-background-color: #12121e; -fx-border-color: transparent;");
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

    // ── Refresh (rebuilds cards from GameState) ───────────────────────────────

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
            if (armedCard != null) armedCard.setArmed(false);
            armedCard = card;
            card.setArmed(true);
            if (onPlaceEntity != null) onPlaceEntity.accept(entity);
        });

        card.setOnDetails(e -> {
            if (onDetailsEntity != null) onDetailsEntity.accept(e);
        });

        card.setOnDelete(e -> {
            // Remove from GameState
            if (e instanceof PlayerEntity)    gameState.removePlayer(e.getId());
            if (e instanceof CharacterEntity) gameState.removeCharacter(e.getId());
            refresh();
            if (onEntitiesChanged != null) onEntitiesChanged.run();
        });

        return card;
    }

    // ── Add player via folder browser ─────────────────────────────────────────

    private void addPlayer() {
        File folder = FileHelper.browseForCharacterFolder(ownerStage);
        if (folder == null) return;

        String name = folder.getName();
        String id   = FileHelper.generateId(name);

        PlayerEntity player = new PlayerEntity(id, name, 5.0); // default 5ft (medium)
        player.setAvatarPath(resolveAsset(folder, "Avatar.png"));
        player.setDetailsPath(resolveAsset(folder, "Details.png"));

        // Show a quick dialog to set size
        double size = promptForSize(name, 5.0);
        player.setSizeInFeet(size);

        gameState.addPlayer(player);
        refresh();
        if (onEntitiesChanged != null) onEntitiesChanged.run();
    }

    // ── Add character/NPC via folder browser ──────────────────────────────────

    private void addCharacter() {
        File folder = FileHelper.browseForCharacterFolder(ownerStage);
        if (folder == null) return;

        String name = folder.getName();
        String id   = FileHelper.generateId(name);

        CharacterEntity character = new CharacterEntity(id, name, 5.0);
        character.setAvatarPath(resolveAsset(folder, "Avatar.png"));
        character.setDetailsPath(resolveAsset(folder, "Details.png"));

        double size = promptForSize(name, 5.0);
        character.setSizeInFeet(size);

        gameState.addCharacter(character);
        refresh();
        if (onEntitiesChanged != null) onEntitiesChanged.run();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                .map(s -> { try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultSize; } })
                .orElse(defaultSize);
    }

    /** Disarms the currently armed card (after placement or cancellation). */
    public void disarmCard() {
        if (armedCard != null) {
            armedCard.setArmed(false);
            armedCard = null;
        }
    }

    // ── Callback setters ──────────────────────────────────────────────────────

    public void setOnPlaceEntity(Consumer<Entity> handler)   { this.onPlaceEntity = handler; }
    public void setOnDetailsEntity(Consumer<Entity> handler) { this.onDetailsEntity = handler; }
    public void setOnEntitiesChanged(Runnable handler)       { this.onEntitiesChanged = handler; }
}
