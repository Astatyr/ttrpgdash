package ttrpgdash;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import ttrpgdash.model.GameState;
import ttrpgdash.model.PlayerEntity;
import ttrpgdash.model.StatusEffect;
import ttrpgdash.util.FileHelper;

/**
 * Horizontal bar displayed at the bottom of the player view.
 * Each PlayerEntity is shown as a circular avatar whose centre aligns with the
 * bar's horizontal midline, with the entity name below and status icons above.
 * All content sizes scale proportionally with the bar's preferred height.
 */
public final class PlayerBar extends HBox {

    private static final double DEFAULT_HEIGHT = 160;

    private final GameState gameState;

    /**
     * Creates the player bar bound to the given game state.
     */
    public PlayerBar(GameState gameState) {
        this.gameState = gameState;
        applyBarBackground();
        setAlignment(Pos.CENTER);
        refresh();

        prefHeightProperty().addListener((obs, oldH, newH) -> {
            if (newH.doubleValue() > 10) {
                refresh();
            }
        });
    }

    /**
     * Rebuilds the bar from the current player list in GameState.
     * Call this whenever players are added, removed, or their status changes.
     */
    public void refresh() {
        getChildren().clear();
        addSpacer();
        for (PlayerEntity player : gameState.getPlayers()) {
            getChildren().add(buildCard(player));
            addSpacer();
        }
    }

    private void applyBarBackground() {
        String path = "assets/playerbarbg.png";
        if (FileHelper.fileExists(path)) {
            String uri = new File(path).toURI().toString();
            setStyle("-fx-background-image: url('" + uri + "'); "
                    + "-fx-background-size: 100% 100%; "
                    + "-fx-border-color: #1a1a3a; -fx-border-width: 1 0 0 0;");
        } else {
            setStyle("-fx-background-color: #0d0d1a; "
                    + "-fx-border-color: #1a1a3a; -fx-border-width: 1 0 0 0;");
        }
    }

    private void addSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
    }

    private double barH() {
        double h = getPrefHeight();
        return h > 10 ? h : DEFAULT_HEIGHT;
    }

    private double avatarSize() {
        return barH() * 0.52;
    }

    private double nameFontSize() {
        return Math.max(9, barH() * 0.11);
    }

    private double iconSize() {
        return Math.max(10, barH() * 0.17);
    }

    private double dotRadius() {
        return Math.max(5, barH() * 0.06);
    }

    /** Each card is a StackPane: avatar centred on the midline, name below, status above. */
    private StackPane buildCard(PlayerEntity player) {
        double cardW = Math.max(avatarSize() * 1.4, 80);
        StackPane card = new StackPane();
        card.setMinWidth(cardW);
        card.setPrefWidth(cardW);

        StackPane avatar = buildAvatar(player);
        StackPane.setAlignment(avatar, Pos.CENTER);

        Label name = buildName(player);
        StackPane.setAlignment(name, Pos.BOTTOM_CENTER);
        StackPane.setMargin(name, new Insets(0, 0, barH() * 0.03, 0));

        card.getChildren().addAll(avatar, name);
        return card;
    }

    private StackPane buildAvatar(PlayerEntity player) {
        double size = avatarSize();
        double radius = size / 2.0;
        StackPane pane = new StackPane();
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        if (FileHelper.fileExists(player.getAvatarPath())) {
            ImageView img = new ImageView(FileHelper.loadImage(player.getAvatarPath()));
            img.setFitWidth(size);
            img.setFitHeight(size);
            img.setPreserveRatio(false);
            img.setClip(new Circle(radius, radius, radius));
            pane.getChildren().add(img);
        } else {
            Circle bg = new Circle(radius);
            bg.setFill(Color.web("#2a5a8a"));
            String initials = player.getName().length() >= 2
                    ? player.getName().substring(0, 2).toUpperCase()
                    : player.getName().toUpperCase();
            Label init = new Label(initials);
            init.setStyle("-fx-text-fill: white; -fx-font-size: " + (int) (size * 0.27)
                    + "px; -fx-font-weight: bold;");
            pane.getChildren().addAll(bg, init);
        }

        Circle border = new Circle(radius);
        border.setFill(Color.TRANSPARENT);
        border.setStroke(Color.web("#6fa8dc"));
        border.setStrokeWidth(Math.max(1.5, size * 0.04));
        pane.getChildren().add(border);

        // Status icons overlaid at the top of the avatar circle.
        // StackPane centres children by default; translateY offsets from that centre
        // so the row sits near the top edge with a small inset.
        HBox statusRow = buildStatusRow(player);
        double iconH = iconSize();
        double topInset = size * 0.1;
        statusRow.setTranslateY(topInset - iconH - size / 2.0);
        pane.getChildren().add(statusRow);

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.color(0, 0, 0, 0.65));
        shadow.setRadius(size * 0.18);
        shadow.setOffsetX(size * 0.04);
        shadow.setOffsetY(size * 0.06);
        pane.setEffect(shadow);

        return pane;
    }

    private Label buildName(PlayerEntity player) {
        Label name = new Label(player.getName());
        name.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: " + (int) nameFontSize() + "px;");
        return name;
    }

    private HBox buildStatusRow(PlayerEntity player) {
        HBox row = new HBox(barH() * 0.02);
        row.setAlignment(Pos.CENTER);
        row.setMinHeight(iconSize());

        for (String effect : player.getStatusEffects()) {
            String path = StatusEffect.iconPath(effect);
            if (FileHelper.fileExists(path)) {
                ImageView icon = new ImageView(FileHelper.loadImage(path));
                icon.setFitWidth(iconSize());
                icon.setFitHeight(iconSize());
                row.getChildren().add(icon);
            } else {
                Circle dot = new Circle(dotRadius());
                dot.setFill(Color.LIGHTGRAY);
                row.getChildren().add(dot);
            }
        }

        return row;
    }
}
