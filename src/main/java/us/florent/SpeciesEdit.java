/*
 * speciesEdit.java
 *
 */
package us.florent;

import com.mongodb.client.model.ReplaceOptions;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bson.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author florent Charpin
 */
public class SpeciesEdit extends Application {

    private final GenReef4 db = new GenReef4();
    private List<String> dist_a;
    private ObservableList<String> locations = null;
    private List<String> types;
    private GenReef4.Species node;

    public static class PhotoRow {
        private final SimpleStringProperty thumb = new SimpleStringProperty("");
        private final SimpleStringProperty location = new SimpleStringProperty("");
        private final SimpleStringProperty type = new SimpleStringProperty("");
        private final SimpleStringProperty comment = new SimpleStringProperty("");

        public PhotoRow() {}

        public PhotoRow(int thumb, String location, String type, String comment) {
            this.thumb.set(String.valueOf(thumb));
            this.location.set(location != null ? location : "");
            this.type.set(type != null ? type : "");
            this.comment.set(comment != null ? comment : "");
        }

        public String getThumb() { return thumb.get(); }
        public void setThumb(String v) { thumb.set(v); }
        public SimpleStringProperty thumbProperty() { return thumb; }

        public String getLocation() { return location.get(); }
        public void setLocation(String v) { location.set(v); }
        public SimpleStringProperty locationProperty() { return location; }

        public String getType() { return type.get(); }
        public void setType(String v) { type.set(v); }
        public SimpleStringProperty typeProperty() { return type; }


        public String getComment() { return comment.get(); }
        public void setComment(String v) { comment.set(v); }
        public SimpleStringProperty commentProperty() { return comment; }
    }

    // Form fields
    private TextField IDTextField;
    private TextField NameTextField;
    private TextField sciTextField;
    private TextField subgenusTextField;
    private TextField taxorefTextField;
    private TextField depth1TextField;
    private TextField depth2TextField;
    private TextField sizeTextField;
    private TextField disp1TextField;
    private TextField disp2TextField;
    private TextField disp3TextField;
    private TextField disp4TextField;
    private TextField thumbTextField;
    private TextField thumb2TextField;
    private TextField thumb3TextField;
    private TextField thumb4TextField;
    private TextField akaTextField;
    private TextField asnTextField;
    private TextField distTextField;
    private CheckBox endemicCheckBox;
    private TextField noteTextField;
    private TableView<PhotoRow> photoTable;
    private ObservableList<PhotoRow> photoData;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.SEVERE);

        initDB();

        stage.setTitle("Reef Species Editor");
        stage.setScene(new Scene(buildUI(), 680, 800));
        stage.show();

        fillValues();
        populateTable(null);

        // Default first row
        PhotoRow defaultRow = new PhotoRow();
        defaultRow.setThumb("1");
        if (photoData.isEmpty()) {
            photoData.add(defaultRow);
        } else {
            photoData.getFirst().setThumb("1");
        }
        thumbTextField.setText("1");
    }

    private void initDB() throws IOException {
        try {
            db.createTaxonTree();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        db.basepathIndexAll = "/home/fc/web/reef4";
        db.buildAllData("reeflist4", 0);

        dist_a = db.species_collection.getAllSpecies().stream()
                .flatMap(s -> s.dist().stream()).sorted().distinct()
                .collect(Collectors.toList());
        node = null;
        if(locations == null) {
            locations = FXCollections.observableArrayList(db.species_collection.getAllSpecies().stream()
                    .flatMap(s -> s.photo().stream()).map(GenReef4.Photo::location)
                    .sorted().distinct().collect(Collectors.toList()));
            locations.addFirst("");
        }
        types = db.species_collection.getAllSpecies().stream()
                .flatMap(s -> s.photo().stream()).filter(p -> p.type() != null)
                .map(GenReef4.Photo::type).sorted().distinct()
                .collect(Collectors.toList());

        types.addFirst("");
    }

    private void fillValues() {
    }

    private VBox buildUI() {
        // Create all fields
        IDTextField = new TextField();
        NameTextField = new TextField();
        sciTextField = new TextField();
        subgenusTextField = new TextField();
        taxorefTextField = new TextField();
        depth1TextField = new TextField();
        depth1TextField.setPrefWidth(45);
        depth2TextField = new TextField();
        depth2TextField.setPrefWidth(45);
        sizeTextField = new TextField();
        disp1TextField = new TextField();
        disp2TextField = new TextField();
        disp3TextField = new TextField();
        disp4TextField = new TextField();
        thumbTextField = new TextField();
        thumbTextField.setPrefWidth(45);
        thumb2TextField = new TextField();
        thumb2TextField.setPrefWidth(45);
        thumb3TextField = new TextField();
        thumb3TextField.setPrefWidth(45);
        thumb4TextField = new TextField();
        thumb4TextField.setPrefWidth(45);
        akaTextField = new TextField();
        asnTextField = new TextField();
        distTextField = new TextField();
        endemicCheckBox = new CheckBox("Endemic");
        noteTextField = new TextField();

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> loadButtonAction());
        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveButtonAction());
        Button distSelect = new Button("...");
        distSelect.setOnAction(e -> distSelectAction());

        // ID action: auto-fill name if empty
        IDTextField.setOnAction(e -> {
            if (NameTextField.getText().isEmpty())
                NameTextField.setText(IDTextField.getText());
        });
        IDTextField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && NameTextField.getText().isEmpty())
                NameTextField.setText(IDTextField.getText());
        });

        // Layout using GridPane for the form
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.setPadding(new Insets(10));

        int row = 0;

        // Row: ID + Load/Save
        form.add(new Label("ID"), 0, row);
        HBox idRow = new HBox(5, IDTextField, loadButton, saveButton);
        HBox.setHgrow(IDTextField, Priority.ALWAYS);
        IDTextField.setPrefWidth(400);
        form.add(idRow, 1, row);
        row++;

        // Row: Name
        form.add(new Label("Name"), 0, row);
        NameTextField.setPrefWidth(400);
        form.add(NameTextField, 1, row);
        row++;

        // Row: Sci Name
        form.add(new Label("Sci. Name"), 0, row);
        sciTextField.setPrefWidth(400);
        form.add(sciTextField, 1, row);
        row++;

        // Row: Subgenus
        form.add(new Label("Subgenus"), 0, row);
        subgenusTextField.setPrefWidth(400);
        form.add(subgenusTextField, 1, row);
        row++;

        // Row: Taxoref
        form.add(new Label("Taxoref"), 0, row);
        form.add(taxorefTextField, 1, row);
        row++;

        // Row: Depth + Size
        form.add(new Label("Depth"), 0, row);
        HBox depthRow = new HBox(5, depth1TextField, new Label("-"), depth2TextField, new Label("Size"), sizeTextField);
        depthRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(sizeTextField, Priority.ALWAYS);
        form.add(depthRow, 1, row);
        row++;

        // Rows: Disp 1/2/3 + thumbs
        Label thumbLabel1 = new Label("thumb");
        Label thumbLabel2 = new Label("thumb");
        Label thumbLabel3 = new Label("thumb");
        Label thumbLabel4 = new Label("thumb");
        thumbLabel1.setMinWidth(40);
        thumbLabel2.setMinWidth(40);
        thumbLabel3.setMinWidth(40);
        thumbLabel4.setMinWidth(40);
        thumbTextField.setMinWidth(45);
        thumb2TextField.setMinWidth(45);
        thumb3TextField.setMinWidth(45);
        thumb4TextField.setMinWidth(45);

        form.add(new Label("Disp 1"), 0, row);
        HBox disp1Row = new HBox(5, disp1TextField, thumbLabel1, thumbTextField);
        disp1Row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(disp1TextField, Priority.ALWAYS);
        form.add(disp1Row, 1, row);
        row++;

        form.add(new Label("Disp 2"), 0, row);
        HBox disp2Row = new HBox(5, disp2TextField, thumbLabel2, thumb2TextField);
        disp2Row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(disp2TextField, Priority.ALWAYS);
        form.add(disp2Row, 1, row);
        row++;

        form.add(new Label("Disp 3"), 0, row);
        HBox disp3Row = new HBox(5, disp3TextField, thumbLabel3, thumb3TextField);
        disp3Row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(disp3TextField, Priority.ALWAYS);
        form.add(disp3Row, 1, row);
        row++;

        form.add(new Label("Disp 4"), 0, row);
        HBox disp4Row = new HBox(5, disp4TextField, thumbLabel4, thumb4TextField);
        disp4Row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(disp4TextField, Priority.ALWAYS);
        form.add(disp4Row, 1, row);
        row++;

        // Row: AKA
        form.add(new Label("AKA"), 0, row);
        form.add(akaTextField, 1, row);
        row++;

        // Row: Alt Sci
        form.add(new Label("Alt. Sci."), 0, row);
        form.add(asnTextField, 1, row);
        row++;

        // Row: Note
        form.add(new Label("Note"), 0, row);
        form.add(noteTextField, 1, row);
        row++;

        // Row: Distribution
        form.add(new Label("Dist."), 0, row);
        HBox distRow = new HBox(5, distTextField, distSelect);
        HBox.setHgrow(distTextField, Priority.ALWAYS);
        form.add(distRow, 1, row);
        row++;

        // Row: Endemic field
        form.add(endemicCheckBox, 1, row);
        row++;

        // Make column 1 grow
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPrefWidth(70);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(col0, col1);

        // Photo table
        photoTable = new TableView<>();
        photoTable.setEditable(true);
        photoData = FXCollections.observableArrayList();
        // Pre-populate with empty rows
        for (int i = 0; i < 50; i++) {
            photoData.add(new PhotoRow());
        }
        photoTable.setItems(photoData);

        TableColumn<PhotoRow, String> thumbCol = new TableColumn<>("Thumb");
        thumbCol.setCellValueFactory(cd -> cd.getValue().thumbProperty());
        thumbCol.setCellFactory(col -> createCommitOnFocusLossCell());
        thumbCol.setOnEditCommit(e -> e.getRowValue().setThumb(e.getNewValue()));
        thumbCol.setPrefWidth(70);
        thumbCol.setMaxWidth(70);

        TableColumn<PhotoRow, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd -> cd.getValue().locationProperty());
        locCol.setCellFactory(col -> new TableCell<>() {
            private ComboBox<String> comboBox;

            @Override
            public void startEdit() {
                super.startEdit();
                comboBox = new ComboBox<>(locations);
                comboBox.setValue(getItem());
                comboBox.setMaxWidth(Double.MAX_VALUE);
                comboBox.setOnAction(ev -> {
                    String val = comboBox.getValue();
                    commitEdit(val);
                });
                comboBox.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) cancelEdit();
                });
                setText(null);
                setGraphic(comboBox);
                comboBox.show();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (isEditing()) {
                    if (comboBox != null) comboBox.setValue(item);
                    setText(null);
                    setGraphic(comboBox);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        locCol.setOnEditCommit(e -> {
            e.getRowValue().setLocation(e.getNewValue());
            String sel = e.getNewValue();
            if (sel != null && !sel.isBlank()) {
                locations.remove(sel);
                locations.add(1, sel);
            }
        });

        TableColumn<PhotoRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd -> cd.getValue().typeProperty());
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(types)));
        typeCol.setOnEditCommit(e -> e.getRowValue().setType(e.getNewValue()));
        typeCol.setPrefWidth(130);
        typeCol.setMaxWidth(130);

        TableColumn<PhotoRow, String> commentCol = new TableColumn<>("Comment");
        commentCol.setCellValueFactory(cd -> cd.getValue().commentProperty());
        commentCol.setCellFactory(col -> createCommitOnFocusLossCell());
        commentCol.setOnEditCommit(e -> e.getRowValue().setComment(e.getNewValue()));

        photoTable.getColumns().addAll(thumbCol, locCol, typeCol, commentCol);
        photoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_LAST_COLUMN);

        // Context menu for table
        ContextMenu contextMenu = new ContextMenu();
        MenuItem moveUp = new MenuItem("Move Up");
        moveUp.setOnAction(e -> moveRowUp());
        MenuItem moveDown = new MenuItem("Move Down");
        moveDown.setOnAction(e -> moveRowDown());
        MenuItem insertItem = new MenuItem("Insert");
        insertItem.setOnAction(e -> insertRow());
        MenuItem removeItem = new MenuItem("Remove");
        removeItem.setOnAction(e -> removeRow());
        contextMenu.getItems().addAll(moveUp, moveDown, insertItem, removeItem);
        photoTable.setContextMenu(contextMenu);

        // Menu bar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New");
        newItem.setOnAction(e -> newAction());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().addAll(newItem, exitItem);

        Menu editMenu = new Menu("Edit");
        MenuItem reloadItem = new MenuItem("Reload");
        reloadItem.setOnAction(e -> reloadAction());
        MenuItem newLocItem = new MenuItem("New Location");
        editMenu.getItems().addAll(reloadItem, newLocItem);

        menuBar.getMenus().addAll(fileMenu, editMenu);

        for (TextField tf : List.of(IDTextField, NameTextField, sciTextField, subgenusTextField,
                taxorefTextField, depth1TextField, depth2TextField, sizeTextField,
                disp1TextField, disp2TextField, disp3TextField, disp4TextField,
                thumbTextField, thumb2TextField, thumb3TextField, thumb4TextField,
                akaTextField, asnTextField, distTextField, noteTextField)) {
            setupPrimaryClipboard(tf);
        }

        VBox root = new VBox(menuBar, form, photoTable);
        VBox.setVgrow(photoTable, Priority.ALWAYS);
        return root;
    }

    final void populateTable(List<GenReef4.Photo> thumbList) {
        photoData.clear();
        if (thumbList != null) {
            for (GenReef4.Photo tl : thumbList) {
                photoData.add(new PhotoRow(tl.id(), tl.location(), tl.type(), tl.comment()));
            }
        }
        // Pad to 50 rows
        while (photoData.size() < 50) {
            photoData.add(new PhotoRow());
        }
    }

    private void loadButtonAction() {
        ListDialog.SpeciesInfo[] list = db.species_collection.getAllSpecies()
                .stream()
                .map(s -> new ListDialog.SpeciesInfo(s.id(), s.name(), s.sciName()))
                .sorted((a, b) -> a.id.compareTo(b.id))
                .toArray(ListDialog.SpeciesInfo[]::new);
        String sel = ListDialog.showDialog(primaryStage, list, IDTextField.getText());
        if (sel == null) {
            return;
        }
        loadSpecies(sel);
    }

    private void loadSpecies(String sel) {
        newAction();
        node = getNode(sel);
        IDTextField.setText(sel);
        if (node == null) return;

        NameTextField.setText(node.name());
        sciTextField.setText(node.sciName());
        var rec = db.getMongoDB().getCollection("species").find(new Document("id", sel)).first();
        taxorefTextField.setText(rec != null && rec.getString("taxoref") != null ? rec.getString("taxoref") : "");
        subgenusTextField.setText(rec != null && rec.getString("subgenus") != null ? rec.getString("subgenus") : "");

        if (node.depth() != null && !node.depth().isEmpty()) {
            depth1TextField.setText(node.depth().split("-")[0]);
            depth2TextField.setText(node.depth().split("-")[1].split(" ")[0]);
        }
        if (node.size() != null && !node.size().isEmpty()) {
            sizeTextField.setText(node.size());
        }

        disp1TextField.setText(node.dispNames() != null && !node.dispNames().isEmpty() ? node.dispNames().getFirst() : "");
        disp2TextField.setText(node.dispNames() != null && node.dispNames().size() >= 2 ? node.dispNames().get(1) : "");
        disp3TextField.setText(node.dispNames() != null && node.dispNames().size() >= 3 ? node.dispNames().get(2) : "");
        disp4TextField.setText(node.dispNames() != null && node.dispNames().size() >= 4 ? node.dispNames().get(3) : "");

        thumbTextField.setText(!node.thumbs().isEmpty() ? node.thumbs().get(0).toString() : "");
        thumb2TextField.setText(node.thumbs().size() >= 2 ? node.thumbs().get(1).toString() : "");
        thumb3TextField.setText(node.thumbs().size() >= 3 ? node.thumbs().get(2).toString() : "");
        thumb4TextField.setText(node.thumbs().size() >= 4 ? node.thumbs().get(3).toString() : "");

        akaTextField.setText(node.aka());
        asnTextField.setText(node.synonyms());
        distTextField.setText(String.join(",", node.dist()));

        if (node.endemic())
            endemicCheckBox.setSelected(true);

        noteTextField.setText(node.note());
        populateTable(node.photo());
    }

    private void saveButtonAction() {
        if (IDTextField.getText().isEmpty()) {
            showError("No ID entered, cannot save");
            return;
        }
        if (!validateIDField()) {
            showError("Invalid ID format. ID must be a single string starting with a lowercase letter");
            return;
        }

        Document doc = new Document();
        doc.put("id", IDTextField.getText().trim());
        doc.put("Name", NameTextField.getText().trim());
        if (!sciTextField.getText().isBlank())
            doc.put("sciName", sciTextField.getText().trim());
        if (!subgenusTextField.getText().isBlank())
            doc.put("subgenus", subgenusTextField.getText().trim());
        if (!sizeTextField.getText().isBlank())
            doc.put("size", sizeTextField.getText().trim());
        if (!depth1TextField.getText().isBlank())
            doc.put("depth", depth1TextField.getText().trim() + "-" + depth2TextField.getText().trim());
        if (!akaTextField.getText().isBlank())
            doc.put("aka", toStringList(akaTextField.getText().split(",")));
        if (!asnTextField.getText().isBlank())
            doc.put("aSciName", toStringList(asnTextField.getText().split(",")));
        if (endemicCheckBox.isSelected())
            doc.put("endemic", true);
        if (!distTextField.getText().isBlank())
            doc.put("distribution", toStringList(distTextField.getText().split(",")));

        var dispList = new ArrayList<>();
        if (!disp1TextField.getText().isBlank()) dispList.add(disp1TextField.getText().trim());
        if (!disp2TextField.getText().isBlank()) dispList.add(disp2TextField.getText().trim());
        if (!disp3TextField.getText().isBlank()) dispList.add(disp3TextField.getText().trim());
        if (!disp4TextField.getText().isBlank()) dispList.add(disp4TextField.getText().trim());
        if (!dispList.isEmpty())
            doc.put("dispNames", dispList);

        var listInt = new ArrayList<Integer>();
        if (!thumbTextField.getText().isBlank()) listInt.add(Integer.parseInt(thumbTextField.getText().trim()));
        if (!thumb2TextField.getText().isBlank()) listInt.add(Integer.parseInt(thumb2TextField.getText().trim()));
        if (!thumb3TextField.getText().isBlank()) listInt.add(Integer.parseInt(thumb3TextField.getText().trim()));
        if (!thumb4TextField.getText().isBlank()) listInt.add(Integer.parseInt(thumb4TextField.getText().trim()));
        doc.put("thumbs", listInt);

        var listDoc = new ArrayList<Document>();
        for (PhotoRow photoRow : photoData) {
            if (photoRow.getThumb().isBlank() && photoRow.getLocation().isBlank()) continue;
            String img = photoRow.getThumb().trim();
            if (img.isBlank()) continue;
            var p = new Document();
            p.put("id", Integer.parseInt(img));
            if (!photoRow.getLocation().isBlank()) p.put("location", photoRow.getLocation().trim());
            if (!photoRow.getType().isBlank()) p.put("type", photoRow.getType().trim());
            if (!photoRow.getComment().isBlank()) p.put("comment", photoRow.getComment().trim());
            listDoc.add(p);
        }
        doc.put("photos", listDoc);

        if (noteTextField.getText() != null && !noteTextField.getText().isBlank())
            doc.put("note", noteTextField.getText().trim());

        var rec = db.getMongoDB().getCollection("species").find(new Document("id", IDTextField.getText().trim())).first();
        if (rec != null && rec.getDate("update") != null) {
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_MONTH, -30);
            if (rec.getDate("update").before(cal.getTime())) {
                System.out.println("Last saved: " + rec.getDate("update"));
                doc.put("update", new Date());
            } else {
                System.out.println("Saved less than 30 days ago");
                doc.put("update", rec.getDate("update"));
            }
        } else {
            System.out.println("No update tag");
            doc.put("update", new Date());
        }
        if (!taxorefTextField.getText().isBlank()) {
            doc.put("taxoref", taxorefTextField.getText().trim());
        }

        db.getMongoDB().getCollection("species").replaceOne(
                new Document("id", IDTextField.getText().trim()),
                doc, new ReplaceOptions().upsert(true));
        System.out.println("Saved " + IDTextField.getText());

        try {
            String id = IDTextField.getText().trim();
            initDB();
            node = getNode(id);
        } catch (IOException ex) {
            Logger.getLogger(SpeciesEdit.class.getName()).log(Level.SEVERE, null, ex);
        }
        fillValues();
    }

    private void distSelectAction() {
        ArrayList<String> possibleValues = new ArrayList<>(dist_a);
        String[] initialValues;
        if (node == null || node.dist() == null || node.dist().isEmpty()) {
            initialValues = new String[0];
        } else {
            String[] sel = node.dist().toArray(String[]::new);
            for (String sel1 : sel) {
                possibleValues.remove(sel1.trim());
            }
            initialValues = node.dist().toArray(String[]::new);
        }
        var ret = distDialog.showDialog(primaryStage, null, null, possibleValues, new ArrayList<>(Arrays.asList(initialValues)));
        if (ret == null) return;
        distTextField.setText(String.join(",", ret));
        if (node != null) {
            node.dist().removeAll(node.dist());
            node.dist().addAll(ret);
        }
    }

    private void reloadAction() {
        String currentId = IDTextField.getText().trim();
        locations = null;
        try {
            initDB();
        } catch (IOException ex) {
            Logger.getLogger(SpeciesEdit.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        if (!currentId.isEmpty() && getNode(currentId) != null) {
            loadSpecies(currentId);
        }
    }

    private void newAction() {
        IDTextField.setText("");
        NameTextField.setText("");
        sciTextField.setText("");
        subgenusTextField.setText("");
        taxorefTextField.setText("");
        depth1TextField.setText("");
        depth2TextField.setText("");
        sizeTextField.setText("");
        disp1TextField.setText("");
        disp2TextField.setText("");
        disp3TextField.setText("");
        disp4TextField.setText("");
        thumbTextField.setText("");
        thumb2TextField.setText("");
        thumb3TextField.setText("");
        thumb4TextField.setText("");
        akaTextField.setText("");
        asnTextField.setText("");
        distTextField.setText("");
        endemicCheckBox.setSelected(false);
        noteTextField.setText("");
        try {
            initDB();
        } catch (IOException ex) {
            Logger.getLogger(SpeciesEdit.class.getName()).log(Level.SEVERE, null, ex);
        }
        photoData.clear();
        for (int i = 0; i < 50; i++) {
            photoData.add(new PhotoRow());
        }
        photoData.getFirst().setThumb("1");
        thumbTextField.setText("1");
        node = null;
    }

    private void moveRowUp() {
        int idx = photoTable.getSelectionModel().getSelectedIndex();
        if (node == null || idx <= 0 || idx >= node.photo().size()) return;
        GenReef4.Photo up = node.photo().get(idx);
        GenReef4.Photo down = node.photo().get(idx - 1);
        node.photo().set(idx, down);
        node.photo().set(idx - 1, up);
        populateTable(node.photo());
    }

    private void moveRowDown() {
        int idx = photoTable.getSelectionModel().getSelectedIndex();
        if (node == null || idx < 0 || idx >= node.photo().size() - 1) return;
        GenReef4.Photo current = node.photo().get(idx);
        GenReef4.Photo next = node.photo().get(idx + 1);
        node.photo().set(idx, next);
        node.photo().set(idx + 1, current);
        populateTable(node.photo());
    }

    private void insertRow() {
        int idx = photoTable.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            photoData.add(idx, new PhotoRow());
        }
    }

    private void removeRow() {
        int idx = photoTable.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            photoData.remove(idx);
        }
    }

    private boolean validateIDField() {
        String id = IDTextField.getText().trim();
        if (id.isEmpty()) return true;
        if (!Character.isLowerCase(id.charAt(0))) {
            showWarning("ID must start with a lowercase letter");
            IDTextField.requestFocus();
            return false;
        }
        if (id.contains(" ")) {
            showWarning("ID must be a single string (no spaces allowed)");
            IDTextField.requestFocus();
            return false;
        }
        return true;
    }

    private <E> List<String> toStringList(E[] list) {
        List<String> ret = new ArrayList<>();
        for (var e : list) {
            ret.add(e.toString().trim());
        }
        return ret;
    }

    private TableCell<PhotoRow, String> createCommitOnFocusLossCell() {
        return new TableCell<>() {
            private TextField textField;

            @Override
            public void startEdit() {
                super.startEdit();
                textField = new TextField(getItem() != null ? getItem() : "");
                textField.setOnAction(e -> commitEdit(textField.getText()));
                textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused && isEditing()) {
                        commitEdit(textField.getText());
                    }
                });
                setText(null);
                setGraphic(textField);
                textField.selectAll();
                textField.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (isEditing()) {
                    if (textField != null) textField.setText(item != null ? item : "");
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        };
    }

    private static final java.awt.datatransfer.Clipboard PRIMARY =
            Toolkit.getDefaultToolkit().getSystemSelection();

    private static void setupPrimaryClipboard(TextField tf) {
        if (PRIMARY == null) return; // not supported on this platform (Windows/macOS)

        // Copy selection → X11 primary selection
        tf.selectedTextProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                PRIMARY.setContents(new StringSelection(newVal), null);
            }
        });

        // Middle-click → paste from X11 primary selection at caret position
        tf.addEventHandler(MouseEvent.MOUSE_RELEASED, evt -> {
            if (evt.getButton() == MouseButton.MIDDLE) {
                try {
                    Transferable t = PRIMARY.getContents(null);
                    if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                        tf.insertText(tf.getCaretPosition(), text);
                    }
                } catch (Exception ignored) {}
                evt.consume();
            }
        });
    }

    GenReef4.Species getNode(String name) {
        return db.species_collection.getSpecies(name);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "warn");
        launch(args);
    }
}
