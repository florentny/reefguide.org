package us.florent;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeciesTreeTest {

    SpeciesTree tree;

    @BeforeEach
    void setUp() {
        tree = new SpeciesTree();
        // Minimal tree for testing
        tree.addLeaf("Biota", "Animalia", "Kingdom");
        tree.addLeaf("Animalia", "Chordata", "Phylum");
        tree.addLeaf("Chordata", "Actinopterygii", "Class");
        var fam = tree.addLeaf("Actinopterygii", "Pomacentridae", "Family");
        fam.getValue().setCategory("Fish");
        tree.addLeaf("Pomacentridae", "Abudefduf", "Genus");
        var sp = tree.addSpecies("sp1", "Abudefduf", "saxatilis", null, "Sergeant Major");
        tree.setSpeciesCategory(sp);
        //tree.speciesMap.put(sp.getValue().getName(), (SpeciesTree.SpeciesNode) sp.getValue());
        //tree.speciesMap.put(((SpeciesTree.SpeciesNode)sp.getValue()).getId(), (SpeciesTree.SpeciesNode) sp.getValue());
    }

    @Test
    void testBreadthFirstSearch() {
        var node = tree.breadthFirstSearch(tree.depthFirstSearch("Biota"), "Pomacentridae");
        assertNotNull(node);
        assertEquals("Pomacentridae", node.getValue().getName());
    }

    @Test
    void testDepthFirstSearch() {
        var node = tree.depthFirstSearch("Abudefduf");
        assertNotNull(node);
        assertEquals("Abudefduf", node.getValue().getName());
    }

    @Test
    void testAddSpeciesAndFindSpecies() {
        var spNode = tree.addSpecies("sp2", "Abudefduf", "taurus", null,"Taurus Demo");
        assertNotNull(spNode);
        assertEquals("taurus", ((SpeciesTree.SpeciesNode) spNode.getValue()).epithet);

        var found = tree.findSpecies("sp2");
        assertNotNull(found);
        assertEquals("Taurus Demo", found.getName());
    }

    @Test
    void testAddLeaf() {
        var node = tree.addLeaf("Pomacentridae", "Stegastes", "Genus");
        assertNotNull(node);
        assertEquals("Stegastes", node.getValue().getName());
    }

    @Test
    void testInsertNodeBetween() {
        tree.addLeaf("Pomacentridae", "Stegastes", "Genus");
        boolean inserted = tree.insertNodeBetween("Pomacentridae", "Stegastes", "SubfamilyX", "Subfamily");
        assertTrue(inserted);
        var subfamily = tree.depthFirstSearch("SubfamilyX");
        assertNotNull(subfamily);
        assertEquals("Subfamily", subfamily.getValue().getRank());
    }

    @Test
    void testSortTreeByName() {
        tree.addLeaf("Pomacentridae", "_UnknownGenus", "Genus");
        tree.sortTreeByName(tree.depthFirstSearch("Pomacentridae"));
        var unknown = tree.depthFirstSearch("Unknown");
        assertNotNull(unknown);
        assertEquals("Unknown", unknown.getValue().getName());
    }

    @Test
    void testGetAllCategories() {
        var node = tree.depthFirstSearch("Pomacentridae");
        node.getValue().setCategory("Fish");
        Set<String> cats = tree.getAllCategories();
        assertTrue(cats.contains("Fish"));
    }

    @Test
    void testGetAllFamilyNodes() {
        List<SpeciesTree.TreeNode<SpeciesTree.Taxon>> fams = tree.getAllFamilyNodes(tree.depthFirstSearch("Biota"));
        assertFalse(fams.isEmpty());
        assertEquals("Pomacentridae", fams.get(0).getValue().getName());
    }

    @Test
    void testGetAllSpeciesBelowCategory() {
        var fam = tree.depthFirstSearch("Pomacentridae");
        fam.getValue().setCategory("Fish");
        List<SpeciesTree.SpeciesNode> list = tree.getAllSpeciesBelowCategory("Fish");
        assertFalse(list.isEmpty());
        assertEquals("Sergeant Major", list.get(0).getName());
    }

    @Test
    void testDisplayTree() {
        String out = tree.displayTree(tree.depthFirstSearch("Biota"));
        assertTrue(out.contains("Sergeant Major"));
    }

    @Test
    void testExportTreeToCSV() throws IOException {
        File tmp = File.createTempFile("tree", ".csv");
        tree.exportTreeToCSV(tree.depthFirstSearch("Biota"), tmp.getAbsolutePath());
        String content = new String(java.nio.file.Files.readAllBytes(tmp.toPath()));
        assertTrue(content.contains("Sergeant Major"));
        tmp.delete();
    }

    @Test
    void testIsPathInRankOrder() {
        List<SpeciesTree.Taxon> path = new ArrayList<>();
        path.add(new SpeciesTree.Taxon("Animalia", "Kingdom"));
        path.add(new SpeciesTree.Taxon("Chordata", "Phylum"));
        path.add(new SpeciesTree.Taxon("Actinopterygii", "Class"));
        assertTrue(tree.isPathInRankOrder(path));
    }

    @Test
    void testGetAllDangelingLeaf() {
        List<String> leaves = tree.getAllDangelingLeaf(tree.depthFirstSearch("Biota"));
        assertTrue(leaves.isEmpty());
    }

    @Test
    void testGetPathToSpecies() {
        List<SpeciesTree.Taxon> path = tree.getPathToSpecies("sp1");
        assertFalse(path.isEmpty());
        assertEquals("Biota", path.getFirst().getName());
    }

    @Test
    void testGetAllSpeciesSciNAmes() {
        List<String> names = tree.getAllSpeciesSciNAmes(tree.depthFirstSearch("Biota"));
        assertTrue(names.contains("Abudefduf saxatilis"));
    }

    @Test
    void testGetCategoryForSpeciesId() {
        var fam = tree.depthFirstSearch("Pomacentridae");
        //fam.getValue().setCategory("Fish");
        String cat = tree.getCategoryForSpeciesId("sp1");
        assertEquals("Fish", cat);
    }

    @Test
    void testPrintNodeJson() {
        StringBuilder sb = new StringBuilder();
        tree.printNodeJson(tree.depthFirstSearch("Biota"), null, sb);
        assertTrue(sb.toString().contains("\"name\":\"Biota\""));
    }

    @Test
    void testCompareTaxonLists() {
        List<SpeciesTree.Taxon> l1 = List.of(
                new SpeciesTree.Taxon("A", "Kingdom"),
                new SpeciesTree.Taxon("B", "Phylum"),
                new SpeciesTree.Taxon("C", "Class")
        );
        List<SpeciesTree.Taxon> l2 = List.of(
                new SpeciesTree.Taxon("A", "Kingdom"),
                new SpeciesTree.Taxon("B", "Phylum"),
                new SpeciesTree.Taxon("C", "Class")
        );
        // Should not throw
        tree.compareTaxonLists("id", l1, l2);
    }

    @Test
    void testCollectNames() {
        JSONObject json = new JSONObject("{\"scientificname\":\"A\",\"rank\":\"Kingdom\",\"child\":{\"scientificname\":\"B\",\"rank\":\"Phylum\"}}");
        List<SpeciesTree.Taxon> result = new ArrayList<>();
        // Use reflection to access private method
        try {
            var m = SpeciesTree.class.getDeclaredMethod("collectNames", JSONObject.class, List.class);
            m.setAccessible(true);
            m.invoke(tree, json, result);
            assertEquals(2, result.size());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void testGetRestReply() throws Exception {
        // Use a local HTTP server or mock if needed. Here, just check exception for bad URL.
        assertThrows(Exception.class, () -> tree.getRestReply("http://localhost:9999/doesnotexist"));
    }

    @Disabled
    @Test
    void testCreateTreeWithMockedMongo() throws Exception {
        MongoDatabase db = mock(MongoDatabase.class);
        MongoCollection taxonCol = mock(MongoCollection.class);
        MongoCollection speciesCol = mock(MongoCollection.class);

        when(db.getCollection("taxon")).thenReturn(taxonCol);
        when(db.getCollection("species")).thenReturn(speciesCol);

        List<Document> taxonDocs = List.of(
                new Document("name", "Animalia").append("rank", "Kingdom").append("parent", "").append("category", "Animals"),
                new Document("name", "Chordata").append("rank", "Phylum").append("parent", "Animalia").append("category", null)
        );
        AggregateIterable<Document> agg = mock(AggregateIterable.class);
        when(taxonCol.aggregate(anyList())).thenReturn(agg);
        when(agg.iterator()).thenReturn((MongoCursor<Document>) taxonDocs.iterator());

        List<Document> speciesDocs = List.of(
                new Document("id", "spX").append("sciName", "Abudefduf saxatilis").append("Name", "Sergeant Major")
        );
        when(speciesCol.find()).thenReturn((FindIterable<Document>) speciesDocs);

        tree.createTree(db);
        assertNotNull(tree.speciesMap.get("Sergeant Major"));
    }
}
