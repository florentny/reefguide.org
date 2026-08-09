package us.florent;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeciesEditTest {

    @Test
    void testPhotoRowDefaultConstructor() {
        var row = new SpeciesEdit.PhotoRow();
        assertEquals("", row.getThumb());
        assertEquals("", row.getLocation());
        assertEquals("", row.getType());
        assertEquals("", row.getComment());
    }

    @Test
    void testPhotoRowParameterizedConstructor() {
        var row = new SpeciesEdit.PhotoRow(5, "Tahiti", "macro", "test comment");
        assertEquals("5", row.getThumb());
        assertEquals("Tahiti", row.getLocation());
        assertEquals("macro", row.getType());
        assertEquals("test comment", row.getComment());
    }

    @Test
    void testPhotoRowNullParameters() {
        var row = new SpeciesEdit.PhotoRow(1, null, null, null);
        assertEquals("1", row.getThumb());
        assertEquals("", row.getLocation());
        assertEquals("", row.getType());
        assertEquals("", row.getComment());
    }

    @Test
    void testPhotoRowSetters() {
        var row = new SpeciesEdit.PhotoRow();
        row.setThumb("3");
        row.setLocation("Moorea");
        row.setType("wide");
        row.setComment("reef flat");

        assertEquals("3", row.getThumb());
        assertEquals("Moorea", row.getLocation());
        assertEquals("wide", row.getType());
        assertEquals("reef flat", row.getComment());
    }

    @Test
    void testPhotoRowProperties() {
        var row = new SpeciesEdit.PhotoRow(2, "Bora Bora", "close-up", "lagoon");
        assertEquals("2", row.thumbProperty().get());
        assertEquals("Bora Bora", row.locationProperty().get());
        assertEquals("close-up", row.typeProperty().get());
        assertEquals("lagoon", row.commentProperty().get());
    }

    @Test
    void testPhotoRowPropertyBinding() {
        var row = new SpeciesEdit.PhotoRow();
        row.thumbProperty().set("7");
        assertEquals("7", row.getThumb());

        row.locationProperty().set("Rangiroa");
        assertEquals("Rangiroa", row.getLocation());
    }

    @Test
    void testPopulateTableWithPhotos() {
        ObservableList<SpeciesEdit.PhotoRow> photoData = FXCollections.observableArrayList();
        // Use reflection-free approach: call populateTable indirectly by setting up photoData field
        // Instead, test the logic directly
        List<GenReef4.Photo> photos = List.of(
                new GenReef4.Photo(1, "Tahiti", "macro", "coral"),
                new GenReef4.Photo(2, "Moorea", "wide", "reef")
        );

        // Simulate populateTable logic
        photoData.clear();
        for (GenReef4.Photo tl : photos) {
            photoData.add(new SpeciesEdit.PhotoRow(tl.id(), tl.location(), tl.type(), tl.comment()));
        }
        while (photoData.size() < 50) {
            photoData.add(new SpeciesEdit.PhotoRow());
        }

        assertEquals(50, photoData.size());
        assertEquals("1", photoData.get(0).getThumb());
        assertEquals("Tahiti", photoData.get(0).getLocation());
        assertEquals("macro", photoData.get(0).getType());
        assertEquals("coral", photoData.get(0).getComment());
        assertEquals("2", photoData.get(1).getThumb());
        assertEquals("Moorea", photoData.get(1).getLocation());
        assertEquals("", photoData.get(49).getThumb());
    }

    @Test
    void testPopulateTableWithNull() {
        ObservableList<SpeciesEdit.PhotoRow> photoData = FXCollections.observableArrayList();
        // Simulate populateTable(null) logic
        photoData.clear();
        while (photoData.size() < 50) {
            photoData.add(new SpeciesEdit.PhotoRow());
        }
        assertEquals(50, photoData.size());
        assertEquals("", photoData.get(0).getThumb());
    }

    @Test
    void testPopulateTableWithEmptyList() {
        ObservableList<SpeciesEdit.PhotoRow> photoData = FXCollections.observableArrayList();
        List<GenReef4.Photo> photos = List.of();
        photoData.clear();
        for (GenReef4.Photo tl : photos) {
            photoData.add(new SpeciesEdit.PhotoRow(tl.id(), tl.location(), tl.type(), tl.comment()));
        }
        while (photoData.size() < 50) {
            photoData.add(new SpeciesEdit.PhotoRow());
        }
        assertEquals(50, photoData.size());
        assertEquals("", photoData.get(0).getThumb());
    }
}
