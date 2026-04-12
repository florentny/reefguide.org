package us.florent;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class distDialog extends Dialog<List<String>> {

    private final ListView<String> availableList = new ListView<>();
    private final ListView<String> selectedList = new ListView<>();

    private distDialog(List<String> possibleValues, List<String> initialValues) {
        setTitle("Distribution");
        setResizable(true);

        ObservableList<String> availableItems = FXCollections.observableArrayList(possibleValues);
        ObservableList<String> selectedItems = FXCollections.observableArrayList(initialValues);

        availableList.setItems(availableItems);
        availableList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        selectedList.setItems(selectedItems);
        selectedList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // Double-click to move between lists
        availableList.setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2) {
                String sel = availableList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    availableItems.remove(sel);
                    selectedItems.add(sel);
                }
            }
        });

        selectedList.setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2) {
                String sel = selectedList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    selectedItems.remove(sel);
                    availableItems.add(sel);
                }
            }
        });

        SplitPane splitPane = new SplitPane(
                new VBox(new Label("Available"), availableList),
                new VBox(new Label("Selected"), selectedList)
        );
        splitPane.setDividerPositions(0.5);
        VBox.setVgrow(availableList, Priority.ALWAYS);
        VBox.setVgrow(selectedList, Priority.ALWAYS);

        VBox content = new VBox(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        content.setPrefSize(500, 400);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ArrayList<>(selectedItems);
            }
            return null;
        });
    }

    public static List<String> showDialog(Window owner,
                                          String labelText,
                                          String title,
                                          List<String> possibleValues,
                                          List<String> initialValues) {
        distDialog dialog = new distDialog(possibleValues, initialValues);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Optional<List<String>> result = dialog.showAndWait();
        return result.orElse(null);
    }
}
