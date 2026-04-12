package us.florent;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

public class ListDialog extends Dialog<String> {

    public static class SpeciesInfo {
        public final String id;
        public final String name;
        public final String sciName;

        public SpeciesInfo(String id, String name, String sciName) {
            this.id = id;
            this.name = name;
            this.sciName = sciName;
        }

        @Override
        public String toString() {
            return String.format("%s - %s (%s)", id, name, sciName);
        }
    }

    private final ListView<Object> listView = new ListView<>();
    private final TextField searchField = new TextField();
    private Object[] allPossibleValues;

    private ListDialog(Object[] possibleValues, String initialValue) {
        setTitle("Species List");
        setResizable(true);

        this.allPossibleValues = possibleValues;
        listView.setItems(FXCollections.observableArrayList(possibleValues));

        // Select initial value
        if (initialValue != null) {
            for (Object item : possibleValues) {
                if (item instanceof SpeciesInfo info) {
                    if (info.id.equals(initialValue)) {
                        listView.getSelectionModel().select(item);
                        listView.scrollTo(item);
                        break;
                    }
                } else if (item instanceof String s) {
                    if (s.equals(initialValue)) {
                        listView.getSelectionModel().select(item);
                        listView.scrollTo(item);
                        break;
                    }
                }
            }
        }

        // Search filtering
        searchField.setPromptText("Search...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterList(newVal));

        Label searchLabel = new Label("Search:");
        HBox searchBox = new HBox(5, searchLabel, searchField);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchBox.setPadding(new Insets(5));

        // Double-click to select
        listView.setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                setResult(extractValue(listView.getSelectionModel().getSelectedItem()));
                close();
            }
        });

        VBox content = new VBox(5, searchBox, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        content.setPrefSize(580, 400);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Object selected = listView.getSelectionModel().getSelectedItem();
                return selected != null ? extractValue(selected) : null;
            }
            return null;
        });
    }

    private String extractValue(Object item) {
        if (item instanceof SpeciesInfo info) {
            return info.id;
        }
        return item != null ? item.toString() : null;
    }

    private void filterList(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            listView.setItems(FXCollections.observableArrayList(allPossibleValues));
            return;
        }
        String lower = searchText.toLowerCase();
        ObservableList<Object> filtered = FXCollections.observableArrayList();
        for (Object item : allPossibleValues) {
            boolean matches = false;
            if (item instanceof SpeciesInfo info) {
                matches = info.id.toLowerCase().contains(lower) ||
                          info.name.toLowerCase().contains(lower) ||
                          info.sciName.toLowerCase().contains(lower);
            } else if (item instanceof String s) {
                matches = s.toLowerCase().contains(lower);
            }
            if (matches) {
                filtered.add(item);
            }
        }
        listView.setItems(filtered);
    }

    public static String showDialog(Window owner, String[] possibleValues, String initialValue) {
        return showDialog(owner, (Object[]) possibleValues, initialValue);
    }

    public static String showDialog(Window owner, Object[] possibleValues, String initialValue) {
        ListDialog dialog = new ListDialog(possibleValues, initialValue);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }
}
