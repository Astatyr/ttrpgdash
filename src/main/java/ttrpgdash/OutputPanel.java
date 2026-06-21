package ttrpgdash;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Collapsible output log panel docked at the bottom of the main window.
 *
 * Accepts tagged log entries — [SYSTEM], [DICE], [SCRIPT] — that appear in a
 * scrollable monospace text area. Collapse/expand via the header toggle.
 * Phase 2 (Lua runtime) will write [DICE] and [SCRIPT] entries here.
 */
public final class OutputPanel extends VBox {

    private static final double EXPANDED_HEIGHT = 130;

    private final TextArea logArea = new TextArea();
    private final Button toggleBtn = new Button("▼ Output");
    private boolean expanded = true;

    /**
     * Creates the output panel in its default expanded state.
     */
    public OutputPanel() {
        setStyle("-fx-background-color: #0d0d1a;"
                + " -fx-border-color: #1a1a3a; -fx-border-width: 1 0 0 0;");

        toggleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666;"
                + " -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 8 2 4;");
        toggleBtn.setOnAction(e -> toggle());

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #444;"
                + " -fx-font-size: 10px; -fx-cursor: hand; -fx-padding: 2 8 2 8;");
        clearBtn.setOnAction(e -> logArea.clear());

        HBox header = new HBox(toggleBtn, clearBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #0a0a16; -fx-padding: 2 4 2 4;");
        HBox.setHgrow(toggleBtn, Priority.ALWAYS);

        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-control-inner-background: #0d0d1a;"
                + " -fx-text-fill: #888; -fx-font-family: monospace;"
                + " -fx-font-size: 11px; -fx-border-color: transparent;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        setPrefHeight(EXPANDED_HEIGHT);
        getChildren().addAll(header, logArea);
    }

    /**
     * Appends a tagged entry to the log.
     * Safe to call from any thread — delegates to the FX thread if needed.
     *
     * @param category one of SYSTEM, DICE, SCRIPT
     * @param message  the log line
     */
    public void log(String category, String message) {
        Platform.runLater(() -> logArea.appendText("[" + category + "] " + message + "\n"));
    }

    private void toggle() {
        expanded = !expanded;
        logArea.setVisible(expanded);
        logArea.setManaged(expanded);
        setPrefHeight(expanded ? EXPANDED_HEIGHT : Region.USE_COMPUTED_SIZE);
        toggleBtn.setText(expanded ? "▼ Output" : "▶ Output");
    }
}
