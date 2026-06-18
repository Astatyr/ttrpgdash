package ttrpgdash.entity;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import ttrpgdash.util.FileHelper;

/**
 * A single row in the sidebar representing one Entity (player or character).
 *
 * Layout:
 *   [Avatar circle] [Name + type label]  [Place] [Details] [Delete]
 *
 * Three callbacks are injected by SidebarPanel:
 *   - onPlace:   user wants to place this entity's token on the map
 *   - onDetails: user wants to view the Details.png
 *   - onDelete:  user wants to remove this entity entirely
 */
public class EntityCard extends HBox {

    private final Entity entity;

    private Runnable onPlace;
    private Consumer<Entity> onDetails;
    private Consumer<Entity> onDelete;

    /**
     * Creates an entity card for the given entity.
     */
    public EntityCard(Entity entity) {
        this.entity = entity;
        buildUI();
    }

    private void buildUI() {
        setSpacing(8);
        setPadding(new Insets(6, 8, 6, 8));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #1e1e2e; -fx-border-color: #2a2a4a; "
                + "-fx-border-width: 0 0 1 0;");

        StackPane avatarPane = buildAvatar();

        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(entity.getName());
        nameLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-font-weight: bold;");

        String typeTag = entity.getEntityType().equals("player") ? "PC" : "NPC";
        String typeColor = entity.getEntityType().equals("player") ? "#6fa8dc" : "#e06c75";
        Label typeLabel = new Label(typeTag + "  •  " + entity.getSizeInFeet() + " ft");
        typeLabel.setStyle("-fx-text-fill: " + typeColor + "; -fx-font-size: 10px;");

        nameBox.getChildren().addAll(nameLabel, typeLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Button placeBtn = makeBtn("Place", "#4a90d9", () -> {
            if (onPlace != null) {
                onPlace.run();
            }
        });
        Button detailsBtn = makeBtn("Details", "#7a6a9e", () -> {
            if (onDetails != null) {
                onDetails.accept(entity);
            }
        });
        Button deleteBtn = makeBtn("✕", "#c0392b", () -> {
            if (onDelete != null) {
                onDelete.accept(entity);
            }
        });

        Tooltip.install(placeBtn, new Tooltip("Click map to place token"));
        Tooltip.install(detailsBtn, new Tooltip("View Details.png"));
        Tooltip.install(deleteBtn, new Tooltip("Remove from session"));

        getChildren().addAll(avatarPane, nameBox, placeBtn, detailsBtn, deleteBtn);
    }

    /** Builds the avatar circle — image if available, otherwise coloured initials. */
    private StackPane buildAvatar() {
        StackPane pane = new StackPane();
        pane.setMinSize(36, 36);
        pane.setMaxSize(36, 36);

        if (FileHelper.fileExists(entity.getAvatarPath())) {
            ImageView img = new ImageView(FileHelper.loadImage(entity.getAvatarPath()));
            img.setFitWidth(36);
            img.setFitHeight(36);
            img.setPreserveRatio(false);
            img.setClip(new Circle(18, 18, 18));
            pane.getChildren().add(img);
        } else {
            // Coloured circle with initials
            String color = entity.getEntityType().equals("player") ? "#2a5a8a" : "#8a2a2a";
            Circle bg = new Circle(18);
            bg.setFill(Color.web(color));
            String initials = entity.getName().length() >= 2
                    ? entity.getName().substring(0, 2).toUpperCase()
                    : entity.getName().toUpperCase();
            Label init = new Label(initials);
            init.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
            pane.getChildren().addAll(bg, init);
        }

        // Border ring
        Circle border = new Circle(18);
        border.setFill(Color.TRANSPARENT);
        String borderColor = entity.getEntityType().equals("player") ? "#6fa8dc" : "#e06c75";
        border.setStroke(Color.web(borderColor));
        border.setStrokeWidth(2);
        pane.getChildren().add(border);

        return pane;
    }

    private Button makeBtn(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; "
                + "-fx-font-size: 11px; -fx-padding: 3 7 3 7; -fx-cursor: hand; "
                + "-fx-background-radius: 4;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    public void setOnPlace(Runnable handler) {
        this.onPlace = handler;
    }

    public void setOnDetails(Consumer<Entity> handler) {
        this.onDetails = handler;
    }

    public void setOnDelete(Consumer<Entity> handler) {
        this.onDelete = handler;
    }

    public void setArmed(boolean armed) {
        setStyle("-fx-background-color: " + (armed ? "#1a2a1a" : "#1e1e2e")
                + "; -fx-border-color: " + (armed ? "#4caf50" : "#2a2a4a")
                + "; -fx-border-width: 0 0 1 0;");
    }

    public Entity getEntity() {
        return entity;
    }
}
