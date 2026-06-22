package ttrpgdash.project;

import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Builds the modal progress dialog shown during project save and load operations.
 * Extracted from {@link ProjectManager} so file I/O logic and UI construction
 * live in separate classes.
 */
final class ProjectProgressDialog {

    private ProjectProgressDialog() {}

    /**
     * Creates and returns a configured modal progress dialog bound to the given task.
     *
     * @param task        the background task whose progress and message are displayed
     * @param ownerStage  parent window for modality
     * @param title       window title
     * @param cancellable if true, pressing X shows a cancel confirmation;
     *                    if false, X is fully consumed and cannot close the dialog
     */
    static Stage build(Task<?> task, Stage ownerStage, String title, boolean cancellable) {
        ProgressBar bar = new ProgressBar();
        bar.progressProperty().bind(task.progressProperty());
        bar.setPrefWidth(320);

        Label message = new Label("Preparing…");
        message.textProperty().bind(task.messageProperty());
        message.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 12px;");

        VBox box = new VBox(10, message, bar);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(20, 24, 20, 24));
        box.setStyle("-fx-background-color: #0d0d1a;");

        Stage dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(ownerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);

        if (cancellable) {
            dialog.setOnCloseRequest(e -> {
                e.consume();
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Cancel the save in progress?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Cancel Save");
                confirm.initOwner(dialog);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) {
                        task.cancel();
                    }
                });
            });
        } else {
            dialog.setOnCloseRequest(Event::consume);
        }

        dialog.setScene(new Scene(box));
        return dialog;
    }
}
