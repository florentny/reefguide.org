package us.florent;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;

public class SpeciesTree {


    List<String> ranks = Arrays.asList("Domain", "Kingdom", "Subkingdom", "Division", "Phylum", "Subphylum", "Parvphylum", "Gigaclass", "Megaclass", "Superclass", "Class", "Subclass",
            "Infraclass", "Subterclass", "Superorder", "Order", "Suborder", "Infraorder", "Superfamily", "Family", "Subfamily", "Tribe", "Genus", "Subgenus", "Species");

    List<TreeNode<Taxon>> families;

    Map<String, SpeciesNode> speciesMap;

    Map<String, String> categoryToSuperCategory;

    public enum SuperCategory {
        FISH("Fish"), INVERTEBRATES("Invertebrates"), SPONGES("Sponges"), CORALS("Corals"), ALGAE("Algae"), MAMMALS("Mammals"), OTHER("Other");

        private final String name;

        SuperCategory(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class Taxon {
        private String name;
        private String rank;
        boolean wasInserted = false;
        private String category = null;
        private String superCategory = null;
        private String orgName = null;
        int AphiaID;
        int numSpecies = 0;


        public int getAphiaID() {
            return AphiaID;
        }

        public void setAphiaID(int aphiaID) {
            AphiaID = aphiaID;
        }


        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSuperCategory() {
            return superCategory;
        }

        public void setSuperCategory(String superCategory) {
            this.superCategory = superCategory;
        }


        public Taxon(String name, String rank) {
            this.name = name.trim();
            this.rank = rank.trim();
        }

        public String getName() {
            return name;
        }

        public String getNameOrOrgName() {
            if(orgName != null && !orgName.isEmpty()) {
                return orgName;
            }
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSortName() {
            return getName();
        }

        public String getSciName() {
            return getName();
        }

        public String getShortSciName() {
            return getName();
        }

        public String getRank() {
            return rank;
        }

        // Number of species under this taxon (populated by populateNumSpecies())
        public int getNumSpecies() {
            return numSpecies;
        }
    }

    public static class SpeciesNode extends Taxon {

        String id = null;
        String genus = null;
        String orgGenus = null; // Original genus name, used when genus is "Unknown"
        String epithet = null;
        String subgenus = null;
        List<Taxon> path;

        public SpeciesNode(String name, String rank) {
            super(name, rank);
        }

        public String getOrgGenus() {
            if(orgGenus == null || orgGenus.isEmpty()) {
                return genus;
            }
            return orgGenus;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }


        @Override
        public String getSortName() {
            return epithet;
        }

        @Override
        public String getSciName() {
            return genus + getSubgenusPart() + epithet;
        }

        private String getSubgenusPart() {
            return (subgenus != null && !subgenus.isEmpty()) ? " (" + subgenus + ") " : " ";
        }

        @Override
        public String getShortSciName() {
            //return genus.charAt(0) + "." + getSubgenusPart() +  epithet;
            return genus.charAt(0) + ". " +  epithet;
        }

    }

    public static class TreeNode<T> {
        private final T value;
        private final List<TreeNode<T>> children;
        private TreeNode<T> parent = null;

        public TreeNode(T value) {
            this.value = value;
            this.children = new ArrayList<>();
        }

        public T getValue() {
            return value;
        }

        public TreeNode<T> getParent() {
            return parent;
        }

        public List<? extends TreeNode<T>> getChildren() {
            return children;
        }

        public void addChild(TreeNode<T> child) {
            child.parent = this;
            children.add(child);
        }
        
    }

    private final TreeNode<Taxon> root = new TreeNode<>(new Taxon("Biota", "Domain"));


    public TreeNode<Taxon> breadthFirstSearch(TreeNode<Taxon> root, String targetName) {
        if(root == null) return null;
        Queue<TreeNode<Taxon>> queue = new LinkedList<>();
        queue.add(root);

        while(!queue.isEmpty()) {
            TreeNode<Taxon> current = queue.poll();
            if(current.getValue().getName().equals(targetName)) {
                return current;
            }
            queue.addAll(current.getChildren());
        }
        return null; // Not found
    }

    public TreeNode<Taxon> depthFirstSearch(String targetName) {
        return depthFirstSearch(root, targetName);
    }

    public TreeNode<Taxon> depthFirstSearch(TreeNode<Taxon> node, String targetName) {
        if(node == null) return null;
        if(node.getValue().getName().equals(targetName)) {
            return node;
        }
        for(TreeNode<Taxon> child : node.getChildren()) {
            TreeNode<Taxon> result = depthFirstSearch(child, targetName);
            if(result != null) {
                return result;
            }
        }
        return null; // Not found
    }

    public SpeciesNode findSpecies(TreeNode<Taxon> node, String name) {
        if(node == null)
            return null;
        if(node.getValue() instanceof SpeciesNode sp) {
            if(sp.getSciName().equals(name))
                return sp;
            if(sp.getName().equals(name))
                return sp;
            if(sp.getId().equals(name))
                return sp;
        }

        for(TreeNode<Taxon> child : node.getChildren()) {
            var result = findSpecies(child, name);
            if(result != null) {
                return result;
            }
        }
        return null; // Not found
    }

    public SpeciesNode findSpecies(String name) {
        if(speciesMap == null || !speciesMap.containsKey(name))
            return findSpecies(root, name);
        return speciesMap.get(name);
    }


    public TreeNode<Taxon> addSpecies(String id, String genus, String epithet, String subgenus, String speciesName) {
        TreeNode<Taxon> genusNode = null;
        if(subgenus != null && !subgenus.isEmpty()) {
            genusNode = depthFirstSearch(root, subgenus);
            if(genusNode != null && genusNode.value.getRank().equals("Genus")) {
                genusNode = genusNode.getChildren().stream().filter(child -> child.getValue().getRank().equals("Subgenus"))
                        .filter(child -> child.getValue().getName().equals(subgenus))
                        .findFirst().orElse(null);

            }
        } else
            genusNode = depthFirstSearch(root, genus);
        if(genusNode == null) {
            return null; // Genus not found
        }
        // Check if the species already exists
        for(TreeNode<Taxon> child : genusNode.getChildren()) {
            if(child.getValue() instanceof SpeciesNode species) {
                if(species.epithet.equals(epithet)) {
                    System.out.println("Species already exists: " + speciesName);
                    return child;
                }
            }
        }
        SpeciesNode sp = new SpeciesNode(speciesName, "Species");
        sp.genus = genus;
        sp.epithet = epithet;
        sp.subgenus = subgenus;
        sp.id = id;
        TreeNode<Taxon> speciesNode = new TreeNode<>(sp);
        if(epithet.equals("Unknown")) {
            sp.orgGenus = sp.genus;
            sp.genus = "Unknown";
            genusNode.getValue().orgName = genusNode.getValue().getName();
            genusNode.getValue().setName("Unknown");
        }
        genusNode.addChild(speciesNode);
        return speciesNode;
    }

    public TreeNode<Taxon> addLeaf(String parentName, String leafName, String leafRank) {
        TreeNode<Taxon> parent = breadthFirstSearch(root, parentName);
        if(parent != null) {
            // Check if the leaf already exists
            for(TreeNode<Taxon> child : parent.getChildren()) {
                if(child.getValue().getName().equals(leafName)) {
                    //System.out.println("Leaf already exists: " + leafName);
                    return child;
                }
            }
            TreeNode<Taxon> leaf = leafRank.equals("Species") ? new TreeNode<>(new SpeciesNode(leafName, leafRank)) : new TreeNode<>(new Taxon(leafName, leafRank));
            parent.addChild(leaf);
            return leaf;
        }
        return null; // Parent not found
    }

    public boolean insertNodeBetween(String parentName, String childName, String newNodeName, String newNodeRank) {
        TreeNode<Taxon> parent = breadthFirstSearch(root, parentName);
        if(parent == null) return false;

        TreeNode<Taxon> child = null;
        for(TreeNode<Taxon> c : parent.getChildren()) {
            if(c.getValue().getName().equals(childName)) {
                child = c;
                break;
            }
        }
        if(child == null) return false;

        // Remove child from parent's children
        parent.getChildren().remove(child);

        // Create new node and insert between
        TreeNode<Taxon> newNode = depthFirstSearch(root, newNodeName);
        if(newNode == null) {
            newNode = new TreeNode<>(new Taxon(newNodeName, newNodeRank));
            newNode.getValue().wasInserted = true;
            parent.addChild(newNode);
        }

        newNode.addChild(child);

        return true;
    }


    void printTree(TreeNode<Taxon> node, String prefix) {
        if(node == null) return;
        System.out.print(prefix + node.getValue().getName() + " (" + node.getValue().getRank() + ")");
        if(node.getValue().getCategory() != null) {
            System.out.print(" [Category: " + node.getValue().getCategory() + "]");
        }
        System.out.println();
        for(TreeNode<Taxon> child : node.getChildren()) {
            printTree(child, prefix + "..");
        }
    }

    public void sortTreeByName(TreeNode<Taxon> node) {
        if(node == null) return;
        if(node.getValue().getName().startsWith("_")) {
            node.getValue().orgName = node.getValue().getName();
            node.getValue().setName("Unknown");
        }
        node.getChildren().sort((a, b) -> a.getValue().getSortName().compareToIgnoreCase(b.getValue().getSortName()));
        for(TreeNode<Taxon> child : node.getChildren()) {
            sortTreeByName(child);
        }
    }

    public String getRestReply(String urlString) throws Exception {
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        var code = conn.getResponseCode();
        if(code != 200 && code != 206) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        StringBuilder sb;
        try(BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())))) {
            sb = new StringBuilder();
            String output;
            while((output = br.readLine()) != null) {
                sb.append(output);
            }
        }
        conn.disconnect();
        return sb.toString();
    }

    public String getRestReply999(String urlString) throws Exception {
        HttpResponse<String> response;
        try(HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlString)).GET().build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        String res = response.body();
        var json = new JSONArray(res);
        for(int i = 0; i < json.length(); i++) {
            if(json.getJSONObject(i).getString("status").equals("accepted")) {
                return json.getJSONObject(i).get("AphiaID").toString();
            }
        }
        return null;
    }

    void createTree(MongoDatabase db) throws Exception {

        MongoCollection<Document> collection = db.getCollection("taxon");

        List<Document> pipeline = asList(
                new Document("$addFields", new Document("rankOrder", new Document("$indexOfArray", asList(ranks, "$rank")))),
                new Document("$sort", new Document("rankOrder", 1))
        );
        // Run the aggregation
        AggregateIterable<Document> sortedDocs = collection.aggregate(pipeline);

        for(Document doc : sortedDocs) {
            String name = doc.get("name").toString();
            if(name.equals("Biota"))
                continue; // Skip root
            String rank = doc.get("rank").toString();
            String parent = doc.get("parent").toString();
            String category = doc.get("category") != null ? doc.get("category").toString() : null;
            Taxon taxon = new Taxon(name, rank);
            taxon.setCategory(category);
            TreeNode<Taxon> node = new TreeNode<>(taxon);
            if(!parent.isEmpty()) {
                //TreeNode<Taxon> parentNode = breadthFirstSearch(root, parent);
                TreeNode<Taxon> parentNode = depthFirstSearch(root, parent);
                if(parentNode != null) {
                    parentNode.addChild(node);
                } else {
                    System.out.println("Parent not found: " + parent + " for " + name);
                    throw new Exception("Parent not found: " + parent);
                }
            } else {
                root.addChild(node);
            }
        }

        categoryToSuperCategory = buildCategoryToSuperCategoryMap(db);

        speciesMap = new HashMap<>();
        collection = db.getCollection("species");
        for(Document doc : collection.find()) {
            String[] sciName = doc.get("sciName").toString().split(" ", 2);
            if(sciName.length < 2) {
                System.out.println("Invalid scientific name: " + doc.get("id").toString() + " - " + doc.get("Name").toString());
                //addSpecies("Unknown", "Unknown", doc.get("Name").toString());
                sciName = new String[]{doc.get("id").toString(), "Unknown"}; // Default to Unknown if invalid
                // continue; // Skip invalid names
            }
            var subgenus = doc.getString("subgenus");
            if(subgenus != null && !subgenus.isEmpty()) {
                Taxon taxon = new Taxon(subgenus, "Subgenus");
                addLeaf(sciName[0], subgenus, "Subgenus");
            }
            var sp = addSpecies(doc.get("id").toString(), sciName[0], sciName[1], subgenus, doc.get("Name").toString());
            if(sp == null) {
                System.out.println("Failed to add species: " + doc.get("id").toString() + " - " + doc.get("Name").toString());
                continue;
            }
            speciesMap.put(sp.getValue().getName(), (SpeciesNode) sp.getValue());
            speciesMap.put(((SpeciesNode)sp.getValue()).getId(), (SpeciesNode) sp.getValue());
            speciesMap.put(doc.get("sciName").toString(), (SpeciesNode) sp.getValue());
            setSpeciesCategory(sp);
        }
        sortTreeByName(depthFirstSearch(root, "Biota"));

        families = getAllFamilyNodes(root);

        // Populate numSpecies count for every node after the tree and species have been added
        populateNumSpecies();
    }

    protected void setSpeciesCategory(TreeNode<Taxon> sp) {
        var parent = sp.getParent();
        while(parent != null) {
            if(parent.getValue().getCategory() != null) {
                String cat = parent.getValue().getCategory();
                sp.getValue().setCategory(cat);
                if(categoryToSuperCategory != null) {
                    sp.getValue().setSuperCategory(categoryToSuperCategory.get(cat));
                }
                return;
            }
            parent = parent.getParent();
        }
    }

    private Map<String, String> buildCategoryToSuperCategoryMap(MongoDatabase db) {
        MongoCollection<Document> collection = db.getCollection("reefconfig");
        Document doc = collection.find(new Document("reef", "reeflist4")).first();
        Map<String, String> map = new HashMap<>();
        if(doc == null) return map;
        for(String superCat : doc.keySet()) {
            if(superCat.equals("_id") || superCat.equals("reef")) continue;
            Object value = doc.get(superCat);
            if(!(value instanceof Document groups)) continue;
            for(String group : groups.keySet()) {
                Object groupValue = groups.get(group);
                if(groupValue instanceof List<?> outerList) {
                    for(Object subList : outerList) {
                        if(subList instanceof List<?> innerList) {
                            for(Object category : innerList) {
                                if(category instanceof String cat) {
                                    map.put(cat, superCat);
                                }
                            }
                        }
                    }
                }
            }
        }
        return map;
    }

    public String getLastCategoryForSpeciesId(String speciesId) {
        //SpeciesNode sp = findSpecies(root, speciesId);
        SpeciesNode sp = findSpecies(speciesId);
        if (sp == null)
            return null;
        List<Taxon> path = getPathToSpecies(root, sp);
        String lastCategory = null;
        for (Taxon taxon : path) {
            if (taxon.getCategory() != null) {
                lastCategory = taxon.getCategory();
            }
        }
        return lastCategory;
    }

    public String getCategoryForSpeciesId(String speciesId) {
        SpeciesNode sp = findSpecies(speciesId);
        if (sp == null)
            return null;
        if(sp.getCategory() != null)
            return sp.getCategory();
        return getLastCategoryForSpeciesId(speciesId);
    }

    public Set<String> getAllCategories() {
        Set<String> categories = new TreeSet<>();
        collectCategories(root, categories);
        return categories;
    }

    private void collectCategories(TreeNode<Taxon> node, Set<String> categories) {
        if (node.getValue().getCategory() != null) {
            categories.add(node.getValue().getCategory());
        }
        for (TreeNode<Taxon> child : node.getChildren()) {
            collectCategories(child, categories);
        }
    }

    public List<TreeNode<Taxon>> getAllFamilyNodes(TreeNode<Taxon> node) {
        List<TreeNode<Taxon>> families = new ArrayList<>();
        if (node.getValue().getRank().equals("Family")) {
            families.add(node);
        }
        for (TreeNode<Taxon> child : node.getChildren()) {
            families.addAll(getAllFamilyNodes(child));
        }
        return families;
    }

    public List<SpeciesNode> getAllSpeciesBelowCategory(String category) {
        List<SpeciesNode> speciesList = new ArrayList<>();
        collectSpeciesBelowCategory(root, category, speciesList);
        return speciesList;
    }

    private void collectSpeciesBelowCategory(TreeNode<Taxon> node, String category, List<SpeciesNode> speciesList) {
        if (category.equals(node.getValue().getCategory())) {
            collectAllSpecies(node, speciesList, category, true);
        } else {
            for (TreeNode<Taxon> child : node.getChildren()) {
                collectSpeciesBelowCategory(child, category, speciesList);
            }
        }
    }

    private void collectAllSpecies(TreeNode<Taxon> node, List<SpeciesNode> speciesList, String category, boolean found) {
        if(node.getValue() instanceof SpeciesNode sp) {
            if(found)
                speciesList.add(sp);
        }
        for(TreeNode<Taxon> child : node.getChildren()) {
            if(child.getValue().getCategory() != null) {
                if(!child.getValue().getCategory().equals(category)) {
                    collectSpeciesBelowCategory(child, category, speciesList);
                    continue;
                }
                    //found = false;
            }
            collectAllSpecies(child, speciesList, category, found);
            found = true;
        }
    }

    public void printNodeJson(TreeNode<Taxon> node, String parentName, StringBuilder jsonOutput) {
        JSONObject obj = new JSONObject();
        obj.put("name", node.getValue().getName());
        obj.put("rank", node.getValue().getRank());
        obj.put("category", node.getValue().getCategory());
        obj.put("parent", parentName);
        jsonOutput.append(obj).append("\n");

        for(TreeNode<Taxon> child : node.getChildren()) {
            if(child.getValue() instanceof SpeciesNode
                    //|| child.getValue().getRank().equals("Subfamily")
                    || child.getValue().getRank().equals("Genus")) {
                continue;
            }
            printNodeJson(child, node.getValue().getName(), jsonOutput);
        }
    }

    public void buildTaxonomy() throws Exception {
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "warn");

        MongoDatabase db;
        MongoClient mongoClient = MongoClients.create();
        db = mongoClient.getDatabase("reef4");

        createTree(db);

        mongoClient.close();

    }

    public String displayTree(TreeNode<Taxon> root) {
        StringBuilder out = new StringBuilder();
        displayTree(root, "", true, out);
        return out.toString();
    }

    private void displayTree(TreeNode<Taxon> node, String indent, boolean isLast, StringBuilder out) {
        out.append(indent);
        if(isLast) {
            if(!indent.isEmpty())
                out.append("└─");
            indent += "  ";
        } else {
            out.append("├─");
            indent += "│ ";
        }

        if(node.getValue() instanceof SpeciesNode species) {
            out.append(species.getShortSciName()).append(" -  ").append(species.getName()).append("\n");
        } else {
            String cat = (node.getValue().getCategory() == null) ? "" : " [" + node.getValue().getCategory() + "]";
            out.append(node.getValue().getName()).append(" (").append(node.getValue().getRank()).append(")").append(" <").
                    append(node.getValue().getNumSpecies()).append(">").append(cat).append("\n");
        }

        for(int i = 0; i < node.children.size(); i++) {
            displayTree(node.children.get(i), indent, i == node.children.size() - 1, out);
        }
    }

    public void exportTreeToCSV(TreeNode<Taxon> root, String filename) throws IOException {
        List<String> cols = new ArrayList<>(ranks);
        cols.removeFirst();
        cols.add("Common Name");
        cols.add("Category");
        FileWriter writer = new FileWriter(filename);
        writer.write(String.join(",", cols) + "\n");

        List<String> path = new ArrayList<>(Collections.nCopies(cols.size(), ""));
        exportTreeToCSVHelper(root, path, cols, writer);
        writer.close();
    }

    private void exportTreeToCSVHelper(TreeNode<Taxon> node, List<String> path, List<String> ranks, FileWriter writer) throws IOException {
        int idx = ranks.indexOf(node.getValue().getRank());
        String cat = node.getValue().getCategory() == null ? "" : " (" + node.getValue().getCategory()+ ")";

        if(idx >= 0)
            path.set(idx, node.getValue().getSortName() + cat);
        else
            System.out.println("Unknown rank: " + node.getValue().getRank() + " for " + node.getValue().getName());
        if(!Objects.isNull(node.getValue().getCategory()))
            path.set(ranks.indexOf("Category"), node.getValue().getCategory());
        if(node.getValue() instanceof SpeciesNode) {
            path.set(ranks.indexOf("Common Name"), node.getValue().getName());
        }

        if(node.getChildren().isEmpty()) {
            writer.write(String.join(",", path) + "\n");
        } else {
            for(TreeNode<Taxon> child : node.getChildren()) {
                exportTreeToCSVHelper(child, new ArrayList<>(path), ranks, writer);
            }
        }
    }

    public boolean isPathInRankOrder(List<Taxon> path) {
        int lastIndex = -1;
        for(Taxon taxon : path) {
            int idx = ranks.indexOf(taxon.getRank());
            if(idx == -1) return false; // Unknown rank
            if(idx <= lastIndex) return false; // Not in order
            lastIndex = idx;
        }
        return true;
    }

    public List<String> getAllDangelingLeaf(TreeNode<Taxon> node) {
        List<String> leaves = new ArrayList<>();
        if(node.getChildren().isEmpty()) {
            if(!node.getValue().getRank().equals("Species")) {
                leaves.add(node.getValue().getName() + " (" + node.getValue().getRank() + ")");
            }
        } else {
            for(TreeNode<Taxon> child : node.getChildren()) {
                leaves.addAll(getAllDangelingLeaf(child));
            }
        }
        return leaves;
    }

    public List<Taxon> getPathToSpecies(String species) {
        SpeciesNode sp = findSpecies(root, species);
        return getPathToSpecies(root, sp);
    }

    public List<Taxon> getPathToSpecies(TreeNode<Taxon> node, SpeciesNode species) {
        List<Taxon> path = new ArrayList<>();
        if(findPathHelper(node, species, path)) {
            return path;
        }
        return Collections.emptyList(); // Not found
    }

    private boolean findPathHelper(TreeNode<Taxon> node, SpeciesNode species, List<Taxon> path) {
        path.add(node.getValue());
        if(node.getValue() instanceof SpeciesNode sp && sp.equals(species)) {
            return true;
        }
        for(TreeNode<Taxon> child : node.getChildren()) {
            if(findPathHelper(child, species, path)) {
                return true;
            }
        }
        path.removeLast();
        return false;
    }


    public List<String> getAllSpeciesSciNAmes(TreeNode<Taxon> node) {
        List<String> leaves = new ArrayList<>();
        if(node.getChildren().isEmpty()) {
            if(node.getValue().getRank().equals("Species")) {
                SpeciesNode sp = (SpeciesNode) node.getValue();
                leaves.add(sp.getSciName());
            }
        } else {
            for(TreeNode<Taxon> child : node.getChildren()) {
                leaves.addAll(getAllSpeciesSciNAmes(child));
            }
        }
        return leaves;
    }

    /**
     * Populate the numSpecies field of each Taxon in the tree.
     * This walks the tree bottom-up and counts the number of Species nodes
     * under each node (including the node itself if it is a Species).
     * Call this once after the tree has been built/updated.
     */
    public void populateNumSpecies() {
        computeNumSpecies(root);
    }

    /**
     * Recursive helper that computes and sets numSpecies on the given node.
     * @param node node to compute for
     * @return number of species under this node
     */
    private int computeNumSpecies(TreeNode<Taxon> node) {
        if(node == null) return 0;
        int count = 0;
        if(node.getValue() instanceof SpeciesNode) {
            count = 1;
        }
        for(TreeNode<Taxon> child : node.getChildren()) {
            count += computeNumSpecies(child);
        }
        node.getValue().numSpecies = count;
        return count;
    }

    void addAphiaIDB() throws Exception {
        System.out.println();
        try(BufferedReader br = new BufferedReader(new FileReader("worms.txt"))) {
            String line;
            while((line = br.readLine()) != null) {
                String[] fields = line.split("\t");
                //System.out.println("Adding AphiaID " + fields[1] + " to " + fields[0]);
                SpeciesNode sp = findSpecies(root, fields[1].replace("*", "").trim());
                if(sp == null) {
                    System.out.println("worms.txt - Species not found: " + fields[1]);
                    continue;
                }
                try {
                    Integer.parseInt(fields[2]);
                } catch(NumberFormatException e) {
                    System.out.println("Invalid AphiaID for species " + fields[1] + ": " + fields[2]);
                    continue;
                }
                sp.setAphiaID(Integer.parseInt(fields[2]));
                List<Taxon> list = getPathToSpecies(root, sp);
                if(!isPathInRankOrder(list)) {
                    System.out.println("Path for " + sp.getName() + " is not in rank order: " + list);
                    throw new Exception("Path for " + sp.getName() + " is not in rank order");
                }
                sp.path = list;
                //list.forEach(t -> System.out.print(t.getName() + "[" + t.getRank() + "] > "));
                //System.out.println();

                List<Taxon> result = new ArrayList<>();
                JSONObject json = new JSONObject(fields[4]);
                collectNames(json, result);
                //result.forEach(t -> System.out.print(t.getName() + "[}" + t.getRank() + "] - "));
                //System.out.println();

                //compareTaxonLists(sp.getName(), list, result);
                compareTaxonLists(sp.getName(), result, list);

            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void compareTaxonLists(String id, List<Taxon> list1, List<Taxon> list2) {

        list2.forEach(t -> {
            if(t.rank.equals("Subgenus")) {
                if(t.name.contains("("))
                    t.name = t.name.substring(t.name.indexOf('(') + 1, t.name.indexOf(')'));
            }
            if(t.rank.contains("("))
                t.rank = t.rank.substring(t.rank.indexOf('(') + 1, t.rank.indexOf(')'));
        });



        for(int i = 2; i < list1.size(); i++) {
            String name = list1.get(i).getSciName().split(" ")[0];
            String rank = list1.get(i).getRank();





            String finalRank = rank;
            var match = list2.stream()
                    .filter(t -> t.getSciName().startsWith(name) && t.getRank().equals(finalRank)).findFirst();

            if(match.isEmpty()) {
                if(name.equals("Gnathostomata") || rank.equals("Subterclass"))
                    continue;
                System.out.println(id + "--> No match for: " + name + " (" + rank + ")");
            }
        }
    }

    private void collectNames(JSONObject node, List<Taxon> result) {
        if(node.has("scientificname")) {
            result.add(new Taxon(node.getString("scientificname"), node.getString("rank")));
        }
        if(node.has("child")) {
            Object child = node.get("child");

            if(child instanceof JSONObject jsonChild) {
                collectNames(jsonChild, result);
            }
        }
    }


    void worms() throws IOException {
        var unknownSpecies = Collections.synchronizedList(new ArrayList<String>());
        List<String> speciesList = getAllSpeciesSciNAmes(root);
        List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger count = new AtomicInteger();
        var customPool = new ForkJoinPool(2);
        try {
            customPool.submit (() ->
                speciesList.parallelStream().limit(5000).forEach(sp -> {
                    String wsp = sp.replace(" ", "%20");
                    try {
                        String currentOutput = "";
                        int currentCount = count.incrementAndGet();
                        currentOutput += currentCount + "\t";
                        System.out.print(currentCount + "\t");

                        String id = getRestReply("https://www.marinespecies.org/rest/AphiaIDByName/" + wsp + "?marine_only=true&extant_only=true");
                        if(id.equals("-999")) {
                            System.out.print(" *** ");
                            currentOutput += " *** ";
                            id = getRestReply999("https://www.marinespecies.org/rest/AphiaRecordsByName/" + wsp + "?marine_only=true&extant_only=true");
                            if(id == null) {
                                throw new Exception("Species not found in WoRMS: " + sp);
                            }
                        }
                        currentOutput += sp + "\t" + id + "\t";
                        System.out.print(sp + "\t" + id + "\t");

                        String rec = getRestReply("https://www.marinespecies.org/rest/AphiaRecordByAphiaID/" + id);
                        JSONObject json = new JSONObject(rec);
                        String status = json.get("status").toString();
                        currentOutput += status + "\t";
                        System.out.print(status + "\t");

                        String taxon = getRestReply("https://www.marinespecies.org/rest/AphiaClassificationByAphiaID/" + id);
                        currentOutput += taxon + "\n";
                        System.out.println(taxon);

                        outputLines.add(currentOutput);
                    } catch(Exception e) {
                        System.out.println("Species: " + sp + " UNKNOWN");
                        unknownSpecies.add(sp);
                    }
                })
            ).get();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            customPool.shutdown();
            try {
                if (!customPool.awaitTermination(1, TimeUnit.MINUTES)) {
                    System.err.println("Custom pool did not terminate in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        unknownSpecies.forEach(s -> System.out.println("UNKNOWN: " + s));
        try(FileWriter writer = new FileWriter("/tmp/worms.txt")) {
            for (String line : outputLines) {
                writer.write(line);
            }
        }
    }


    public static void main(String[] args) throws Exception {

        SpeciesTree speciesTree = new SpeciesTree();
        speciesTree.buildTaxonomy();
        speciesTree.addAphiaIDB();
        // speciesTree.printTree(speciesTree.root, "");

        System.out.println();
        System.out.println();
        speciesTree.sortTreeByName(speciesTree.root);
        String tree = speciesTree.displayTree(speciesTree.depthFirstSearch(speciesTree.root, "Biota"));
        System.out.println(tree);
        try(FileWriter writer = new FileWriter("/home/fc/web/reef4/taxonomy_tree.txt")) {
            writer.write(tree);
        }

        speciesTree.exportTreeToCSV(speciesTree.root, "/home/fc/web/reef4/species.csv");

        List<String> leafNames = speciesTree.getAllDangelingLeaf(speciesTree.root);
        leafNames.forEach(System.out::println);
        System.out.println();
        System.out.println();

        System.exit(0);

        StringBuilder out = new StringBuilder();
        speciesTree.printNodeJson(speciesTree.root, null, out);
        try(FileWriter writer = new FileWriter("/tmp/taxonomy_nodes.json")) {
            writer.write(out.toString());
        }

        speciesTree.worms();


    }

}
