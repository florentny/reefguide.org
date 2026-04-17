package us.florent;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class GenReef4 {

    protected Classifaction group_classification;
    protected SpeciesCollection species_collection;

    static private MongoDatabase db = null;

    private SpeciesTree speciesTree;

    protected static class Page {
        final List<Species> species = new ArrayList<>();
        final List<String> dates = new ArrayList<>();
        final Map<String, String> group = new HashMap<>();
        String name;
        int start;
        int page;
        int index;

        public Page() {
            page = 1;
        }
    }

    protected record Photo(int id, String location, String type, String comment) {
    }

    protected class Classifaction {

        private final Map<String, String> CatSpeeciesType = new HashMap<>();
        private final Map<String, List<String>> groups = new HashMap<>();
        private final Map<String, List<String>> mobilegroups = new HashMap<>();

        void addGroup(String name, List<String> list, Boolean mobile) {
            if(mobile != null && mobile) {
                mobilegroups.put(name, list);
            } else {
                groups.put(name, list);
            }
        }

        List<String> getGroup(String name) {
            return groups.get(name);
        }

        void addCatSpeciesType(String cat, String type) {
            CatSpeeciesType.put(cat, type);
        }

        String getCatSpeciesType(String cat) {
            return CatSpeeciesType.get(cat);
        }

        Set<String> getAllCat() {
            return speciesTree.getAllCategories();
        }
        String getGroupsKey(String cat) {
            var ret = cat;
            for(var it : groups.entrySet()) {
                if(it.getValue().contains(cat))
                    ret =  it.getKey();
            }
            for(var it : mobilegroups.entrySet()) {
                if(it.getValue().contains(ret))
                    return it.getKey();
            }
            return ret;
        }
    }

    protected record Species(String id, String name, String sciName, String subGenus, String size, String depth, boolean endemic,
                             List<String> dist,
                             List<Photo> photo, List<Integer> thumbs, String synonyms, String aka, String note,
                             List<String> dispNames,
                             Date update) {

        String fullSciName() {
            if(subGenus == null || subGenus.isBlank())
                return sciName;
            String[] parts = sciName.split(" ");
            if(parts.length < 2)
                return sciName;
            return parts[0] + " (" + subGenus + ") " + parts[1];
        }

        int getNameCount() {
            if(dispNames == null)
                return 1;
            return dispNames.size();
        }

        String getDispName(int i) {
            if(dispNames == null)
                return name;
            return dispNames.get(i);
        }
    }

    protected class SpeciesCollection {
        private final Map<String, Species> species = new HashMap<>();

        void add(String id, String name, String sciName, String subGenus, String size, String depth, boolean endemic, List<String> dist, List<Document> photos,
                 List<Integer> thumbs, String synonyms, String aka, String note, List<String> dispNames, Date update) {
            List<Photo> p = new ArrayList<>();
            for(Document doc : photos)
                p.add(new Photo(doc.getInteger("id"), doc.getString("location"), doc.getString("type"), doc.getString("comment")));
            p = sortPhotos(p, thumbs);
            if(depth != null) {
                long sd = depthmetric(Integer.parseInt(depth.split("-")[0]));
                long ed = depthmetric(Integer.parseInt(depth.split("-")[1]));
                depth = depth + " ft. (" + sd + "-" + ed + " m)";
            }
            species.put(id, new Species(id, name, sciName.replace("+", ""), subGenus, size, depth, endemic, dist, p, thumbs, synonyms, aka, note, dispNames, update));
        }

        private List<Photo> sortPhotos(List<Photo> ph, List<Integer> thumbs) {
            List<Photo> p = new ArrayList<>();
            thumbs.forEach(n -> {
                var x = ph.stream().filter(r -> r.id == n).findAny().orElseThrow();
                p.add(x);
                ph.remove(x);
            });
            p.addAll(ph.stream().sorted(Comparator.comparingInt(Photo::id).reversed()).toList());
            return p;
        }

        Collection<Species> getAllSpecies() {
            return species.values();
        }

        Species getSpecies(String id) {
            return species.get(id);
        }

        String getCat(String id) {
            String ret = speciesTree.getCategoryForSpeciesId(id);
            if(ret == null) {
                System.out.println("MISSING CAT FOR SPECIES: " + id);
                return "Unknow";
            }
            return ret;
        }

        List<String> getSpeciesNameFromCat(String category) {
            return speciesTree.getAllSpeciesBelowCategory(category).stream().map(SpeciesTree.SpeciesNode::getId)
                    .filter(id -> species_collection.getSpecies(id) != null).toList();
        }

        List<Species> getSpeciesFromCat(String category) {
            return getSpeciesNameFromCat(category).stream().map(x -> species_collection.getSpecies(x)).collect(Collectors.toList());
        }

        List<Species> getLatest() {
            return species.values().stream().filter(s -> s.update != null).sorted(Comparator.comparing(Species::update, Comparator.reverseOrder())).collect(Collectors.toList());
        }

        void validate() {
            species.values().stream().map(Species::sciName).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .forEach((k, v) -> {
                        if(k.isEmpty())
                            return;
                        if(v > 1L) {
                            System.out.println(k + " - " + v);
                            throw new RuntimeException("DUPLICATE SCINAME");
                        }
                    });
        }

    }

    String basepathIndexAll = null;
    public boolean analytics = false;
    protected int numPhotos = 0;
    protected java.util.ArrayList<Page> pageList = new java.util.ArrayList<>();

    //final String[] reefId = {"all", "carib", "indopac", "hawaii", "keys", "baja"};
    final String[] reefName = {"Tropical Reefs", "Caribbean Reefs", "Indo-Pacific Reefs", "South Florida Reefs", "Hawaiian Reefs", "Eastern Pacific Reefs", "French Polynesian Reefs"};
    final String[] preReefName = {"", "Florida, Bahamas and", "Hawaiian, South Pacific and", "", "", "", ""};
    final String[] reefMenu = {"Worldwide", "Caribbean", "Pacific", "South Florida", "Hawaii", "Eastern Pacific", "French Polynesia"};

    static final int numRegion = 7;

    // int __count = 0;

    public GenReef4() {
        //Logger mongoLogger = LoggerFactory.getLogger("org.mongodb.driver");

    }

    static private String overrideCat(String cat, int reefRef) {
        if(reefRef == 2 && cat.equals("Combtooth Blennies"))
            return "Blennies";
        return cat;
    }

    static private String replaceCat(String cat, int reefRef) {

        if(reefRef == 0) {
            return cat.replace("Grunts", "Grunts/Sweetlips");
        }
        if(reefRef == 2) {
            return cat.replace("Combtooth Blennies", "Blennies");
        }
        return cat;
    }

    public void createTaxonTree() throws Exception {
        speciesTree = new SpeciesTree();
        try {
            speciesTree.buildTaxonomy();
            speciesTree.addAphiaIDB();
            speciesTree.addInaturalistIDs();
        } catch(Exception ex) {
            java.util.logging.Logger.getLogger(GenReef4.class.getName()).log(Level.SEVERE, "Cannot Build Taxon Tree", ex);
            throw ex;
        }
    }

    public void createSite(String path, boolean analytics) throws Exception {
        try {
            basepathIndexAll = path;
            String basepathIndexIndoPac = path + "/indopac";
            String basepathIndexHawaii = path + "/hawaii";
            String basepathIndexCarib = path + "/carib";
            String basepathIndexKeys = path + "/keys";
            String basepathIndexBaja = path + "/baja";
            String basepathIndexPolynesia = path + "/fp";
            String[] hearderAll = {"banner1", "banner2", "banner3"};
            String[] hearderCarib = {"banner1", "banner3"};
            String[] hearderIndopac = {"banner2"};
            String[] hearderKeys = {"banner1", "banner3"};
            String[] hearderHawaii = {"banner2"};
            String[] hearderPolynesia = {"banner2"};

            // Get taxon tree
            createTaxonTree();

            this.analytics = analytics;
            System.out.println("================= Site " + path + " =================");
            System.out.println("Processing worldwide:");
            int all = process("reeflist4", basepathIndexAll, 0, hearderAll);
            exportAllSpeciesJson(basepathIndexAll);
            int all_pic = numPhotos;
            System.out.println("Processing Caribbean:");
            int carib = process("reeflistcarib4", basepathIndexCarib, 1, hearderCarib);
            int carib_pic = numPhotos;
            System.out.println("Processing Indo-Pacific:");
            int indopac = process("reeflist4", basepathIndexIndoPac, 2, hearderIndopac);
            int indopac_pic = numPhotos;
            System.out.println("Processing Florida Keys:");
            int keys = process("reeflistcarib4", basepathIndexKeys, 3, hearderKeys);
            int key_pics = numPhotos;
            System.out.println("Processing Hawaii:");
            int hawaii = process("reeflisthawaii4", basepathIndexHawaii, 4, hearderHawaii);
            int hawaii_pics = numPhotos;
            System.out.println("Processing Baja:");
            int baja = process("reeflistbaja4", basepathIndexBaja, 5, hearderHawaii);
            int baja_pics = numPhotos;
            System.out.println("Processing Polynesia:");
            int poly = process("reeflisthawaii4", basepathIndexPolynesia, 6, hearderPolynesia);
            int poly_pics = numPhotos;

            String outString = readFile("about.html");
            outString = outString.replace("__ALL__", Integer.toString(all));
            outString = outString.replace("__ALLPIC__", Integer.toString(all_pic));
            outString = outString.replace("__CARIB__", Integer.toString(carib));
            outString = outString.replace("__CARIBPIC__", Integer.toString(carib_pic));
            outString = outString.replace("__INDOPAC__", Integer.toString(indopac));
            outString = outString.replace("__INDOPACPIC__", Integer.toString(indopac_pic));
            outString = outString.replace("__KEYS__", Integer.toString(keys));
            outString = outString.replace("__KEYSPIC__", Integer.toString(key_pics));
            outString = outString.replace("__HAWAII__", Integer.toString(hawaii));
            outString = outString.replace("__HAWAIIPIC__", Integer.toString(hawaii_pics));
            outString = outString.replace("__BAJA__", Integer.toString(baja));
            outString = outString.replace("__BAJAPIC__", Integer.toString(baja_pics));
            outString = outString.replace("__FPPIC__", Integer.toString(poly_pics));

            if(analytics) {
                outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
            } else {
                outString = outString.replace("__ANALYTICS__", "");
            }

            writeToFile(outString, basepathIndexAll + "/about.html");

            outString = readFile("home.html");
            outString = outString.replace("__ALL__", Integer.toString(all));
            outString = outString.replace("__CARIB__", Integer.toString(carib));
            outString = outString.replace("__INDOPAC__", Integer.toString(indopac));
            outString = outString.replace("__KEYS__", Integer.toString(keys));
            outString = outString.replace("__HAWAII__", Integer.toString(hawaii));
            outString = outString.replace("__BAJA__", Integer.toString(baja));
            outString = outString.replace("__FP__", Integer.toString(poly));

            if(analytics) {
                outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
            } else {
                outString = outString.replace("__ANALYTICS__", "");
            }

            writeToFile(outString, basepathIndexAll + "/home.html");

            outString = readFile("search.html");
            if(analytics) {
                outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
            } else {
                outString = outString.replace("__ANALYTICS__", "");
            }

            writeToFile(outString, basepathIndexAll + "/search.html");

        } catch(IOException ex) {
            java.util.logging.Logger.getLogger(GenReef4.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    void buildAllData(String configFile, int reefRef) {
        buildDB2(configFile, reefRef);

        int index = 1;
        int start = 0;
        for(Page g : pageList) {
            if(g.index == 0) {
                g.index = index++;
            }
            if(g.index < 0) {
                g.index *= -1;
                index = g.index + 1;
            }
            g.start = start;
            start += g.species.size();
        }
    }

    MongoClient mongoClient;

    protected MongoDatabase getMongoDB() {

        if(db == null) {
            String mongoUri = System.getenv("MONGODB_URI");
            mongoClient = mongoUri != null ? MongoClients.create(mongoUri) : MongoClients.create();

            db = mongoClient.getDatabase("reef4");

        }
        return db;
    }

    protected void closeMongoDB() {
        mongoClient.close();
    }

    protected void buildDB2(String Config, int reefRef) {
        try {
            db = getMongoDB();
            loadDataBase(db, reefRef);

            Document config = db.getCollection("reefconfig").find(eq("reef", Config)).first();
            pageList = new java.util.ArrayList<>();
            if(config == null) {
                System.out.println("Null reefconfig");
                return;
            }

            for(String key : config.keySet()) {
                if(key.equals("_id") || key.equals("reef"))
                    continue;
                String catType = key.replace("&", "&amp;");

                Document value = (Document) config.get(key);

                for(var key2 : value.keySet()) {
                    String dir = key2.replace("&", "&amp;");
                    int pagenum = 1;
                    for(var key3 : value.getList(key2, List.class)) {
                        Page _page = new Page();
                        _page.name = replaceCat(dir, reefRef);
                        _page.page = pagenum++;
                        pageList.add(_page);

                        for(var name : key3) {
                            var group = group_classification.getGroup(name.toString());
                            if(group == null) {
                                group = new ArrayList<>();
                                group.add(name.toString());
                            }
                            group.forEach(cat -> {
                                _page.group.put(cat, overrideCat(name.toString(), reefRef));
                                group_classification.addCatSpeciesType(cat, catType);
                                List<String> sl = species_collection.getSpeciesNameFromCat(cat);
                                sl.forEach(sp -> {
                                    var species = species_collection.getSpecies(sp);
                                    _page.species.add(species);
                                });
                            });
                        }

                    }


                }
            }
        } catch(Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private void loadDataBase(MongoDatabase db, int reefRef) {
        group_classification = new Classifaction();

        species_collection = new SpeciesCollection();
        MongoCollection<Document> collection = db.getCollection("species");
        try(MongoCursor<Document> cur = collection.find().iterator()) {
            while(cur.hasNext()) {
                var doc = cur.next();
                var dist = doc.getList("distribution", String.class);
                if(dist == null)
                    System.out.println();
                if(checkRegion(reefRef, dist)) {
                    List<String> aSciName = doc.getList("aSciName", String.class) == null ? Collections.emptyList() : doc.getList("aSciName", String.class);
                    List<String> aka = doc.getList("aka", String.class) == null ? Collections.emptyList() : doc.getList("aka", String.class);
                    String sciname = doc.getString("sciName") == null ? "" : doc.getString("sciName");
                    species_collection.add(doc.getString("id"), doc.getString("Name"), sciname,  doc.getString("subgenus"),
                            doc.getString("size"), doc.getString("depth"), doc.getBoolean("endemic", false),
                            dist, doc.getList("photos", Document.class), doc.getList("thumbs", Integer.class),
                            String.join(", ", aSciName), String.join(", ", aka),
                            doc.getString("note"), doc.getList("dispNames", String.class), doc.getDate("update"));
                }
            }
        }
        species_collection.validate();

        collection = db.getCollection("groups");
        try(MongoCursor<Document> cur = collection.find().iterator()) {
            while(cur.hasNext()) {
                var doc = cur.next();
                group_classification.addGroup(doc.getString("Name"), doc.getList("category", String.class), doc.getBoolean("mobile") );
            }
        }
    }

    protected boolean checkRegion(int reefRef, List<String> dist) {
        if(reefRef == 0)
            return true;
        String comp = String.join("", dist);
        if(reefRef == 1) {
            if(comp.contains("Carib") || comp.contains("Bahamas") || comp.contains("Florida") || comp.contains("Cozumel") || comp.contains("Cayman")
                    || comp.contains("Belize") || comp.contains("Bonaire") || comp.contains("Circum") || comp.toLowerCase().contains("world"))
                return true;
        }
        if(reefRef == 2) {
            if(comp.contains("Palau") || comp.contains("Indo") || comp.contains("Hawaii") || comp.contains("Australia") || comp.contains("Polynesia") || comp.contains("Asia")
                    || comp.contains("Great Barrier") || comp.contains("Micronesia") || comp.contains("Philippines") || comp.contains("Fiji") || comp.contains("Circum") || comp.toLowerCase().contains("world")
                    || comp.contains("West Pacific") || comp.contains("Central Pacific") || comp.contains("Bali"))
                return true;
        }
        if(reefRef == 3) {
            if(comp.contains("Florida") ||
                    comp.contains("Circum") || comp.toLowerCase().contains("world"))
                return true;
        }
        if(reefRef == 4) {
            if(comp.contains("Hawaii") ||
                    comp.contains("Circum") || comp.toLowerCase().contains("world"))
                return true;
        }
        if(reefRef == 5) {
            return comp.contains("California") || comp.contains("Baja") || comp.contains("Pacific Coast")
                    || comp.contains("Circum") || comp.toLowerCase().contains("world");
        }
        if(reefRef == 6) {
            return comp.contains("Polynesia")
                    || comp.contains("Circum") || comp.toLowerCase().contains("world");
        }
        return false;
    }

    protected int depthmetric(int d) {
        long r = Math.round(d * 0.3048);
        r = (r == 49) ? 50 : r;
        r = (r == 76) ? 75 : r;
        r = (r == 24) ? 25 : r;
        r = (r == 61) ? 60 : r;
        r = (r == 46) ? 45 : r;
        r = (r == 14) ? 15 : r;
        r = (r == 9) ? 10 : r;
        r = (r == 11) ? 12 : r;
        r = (r == 183) ? 180 : r;
        r = (r == 366) ? 350 : r;
        r = (r == 213) ? 200 : r;
        r = (r == 79) ? 80 : r;
        r = (r == 91) ? 90 : r;
        r = (r == 122) ? 120 : r;
        r = (r == 101) ? 100 : r;
        r = (r == 73) ? 75 : r;
        return (int) r;
    }

    protected int process(String region,
                          String baseIndex,
                          int reefRef,
                          String[] headers) {

        int headercount = 0;
        numPhotos = 0;
        buildAllData(region, reefRef);
        try {
            genCatalogFiles(species_collection.getAllSpecies(), baseIndex, reefRef, headers[0]);
            for(var g : pageList) {
                genIndexFile(baseIndex, g, reefRef, headers[headercount]);
                headercount = 0;
                for(Species sp : g.species) {
                    genFishFile(sp, baseIndex, reefRef, headers[headercount], g);
                    if(++headercount == headers.length) {
                        headercount = 0;
                    }
                }
            }

            for(var cat : group_classification.getAllCat()) {
                var z = species_collection.getSpeciesFromCat(cat);
                genCatFile(z, baseIndex, reefRef, headers[0], cat);

            }

            String date;
            var latest_list = species_collection.getLatest();
            var latestGroup = new Page();
            String pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern, Locale.ENGLISH);
            for(var sp : latest_list) {
                if(sp == null)
                    continue;
                if(!checkRegion(reefRef, sp.dist))
                    continue;
                if(latestGroup.species.size() > 100)
                    continue;
                date = simpleDateFormat.format(sp.update);
                latestGroup.species.add(sp);
                latestGroup.dates.add(date);

            }
            latestGroup.index = -1;
            genIndexFile(baseIndex, latestGroup, reefRef, headers[0]);
            genRSS(latestGroup, baseIndex);
            updateSearchJson(reefRef);
            exportTaxonomyJson(reefRef);
            copyFile(baseIndex + "/index1.html", baseIndex + "/index.html");

        } catch(IOException ex) {
            ex.printStackTrace();
        }

        System.out.println("Number of species: " + species_collection.getAllSpecies().size());
        return species_collection.getAllSpecies().size();
    }

    private void updateSearchJson(int region) throws IOException {
        StringWriter writer = new StringWriter();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer);
        jsonGenerator.writeStartArray();
        for(var sp : species_collection.getAllSpecies().stream().sorted(Comparator.comparing(Species::fullSciName)).toList()) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("name", sp.id);
            jsonGenerator.writeStringField("fullname", sp.name);
            jsonGenerator.writeStringField("sname", sp.sciName);
            jsonGenerator.writeStringField("subcategory", species_collection.getCat(sp.id));
            var taxon = speciesTree.getPathToSpecies(sp.id);
            jsonGenerator.writeStringField("category", String.join("/", taxon.stream().skip(3).map(t -> {
                if(t instanceof SpeciesTree.SpeciesNode)
                    return "";
                if(t.getCategory() != null && !t.getCategory().contains("and")) {
                    return t.getName() + "(" + t.getCategory() + ")";
                }
                return t.getName();

            }).filter(s -> !s.isEmpty()).toList()));
            jsonGenerator.writeStringField("synonyms", getSpNull(sp.synonyms()));
            jsonGenerator.writeStringField("order", taxon.stream().filter(t -> t.getRank() != null && t.getRank().equals("Order")).map(SpeciesTree.Taxon::getName).findFirst().orElse(""));
            jsonGenerator.writeStringField("family", taxon.stream().filter(t -> t.getRank() != null && t.getRank().equals("Family")).map(SpeciesTree.Taxon::getName).findFirst().orElse(""));
            jsonGenerator.writeStringField("aka", getSpNull(sp.aka()));
            jsonGenerator.writeNumberField("thumb1", sp.thumbs.getFirst());
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.flush();
        writer.flush();
        String json = writer.toString();
        writeToFile(json, basepathIndexAll + "/species_region_" + region + ".json");
    }

    private void exportTaxonomyJson(int region) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Set<String> regionIds = species_collection.getAllSpecies().stream()
                .map(Species::id)
                .collect(Collectors.toSet());
        ObjectNode root = buildTaxonomyJsonNode(mapper, speciesTree.depthFirstSearch("Biota"), regionIds);
        if (root != null) {
            writeToFile(mapper.writeValueAsString(root), basepathIndexAll + "/taxonomy_region_" + region + ".json");
        }
    }

    private ObjectNode buildTaxonomyJsonNode(ObjectMapper mapper, SpeciesTree.TreeNode<SpeciesTree.Taxon> node, Set<String> regionIds) {
        ArrayNode speciesArr = mapper.createArrayNode();
        ArrayNode childrenArr = mapper.createArrayNode();

        for (SpeciesTree.TreeNode<SpeciesTree.Taxon> child : node.getChildren()) {
            if (child.getValue() instanceof SpeciesTree.SpeciesNode sn) {
                if (regionIds.contains(sn.getId())) {
                    Species sp = species_collection.getSpecies(sn.getId());
                    if (sp != null) {
                        int num = sp.getNameCount();
                        for (int j = 0; j < num; j++) {
                            ObjectNode spNode = mapper.createObjectNode();
                            spNode.put("id", sp.id());
                            spNode.put("name", sp.getDispName(j));
                            spNode.put("sname", sp.sciName());
                            spNode.put("thumb", sp.thumbs().get(j));
                            if (sn.getSuperCategory() != null) spNode.put("superCat", sn.getSuperCategory());
                            speciesArr.add(spNode);
                        }
                    }
                }
            } else {
                ObjectNode childNode = buildTaxonomyJsonNode(mapper, child, regionIds);
                if (childNode != null) childrenArr.add(childNode);
            }
        }

        if (speciesArr.isEmpty() && childrenArr.isEmpty()) return null;

        ObjectNode obj = mapper.createObjectNode();
        obj.put("name", node.getValue().getName());
        obj.put("rank", node.getValue().getRank());
        if (node.getValue().getCategory() != null) obj.put("category", node.getValue().getCategory());
        obj.set("children", childrenArr);
        obj.set("species", speciesArr);
        return obj;
    }

    private void exportAllSpeciesJson(String baseIndex) throws IOException {
        StringWriter writer = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(writer);
        gen.writeStartArray();
        for (Species sp : species_collection.getAllSpecies().stream().sorted(Comparator.comparing(Species::id)).toList()) {
            gen.writeStartObject();
            gen.writeStringField("id", sp.id());
            gen.writeStringField("name", sp.name());
            gen.writeStringField("sciName", sp.sciName());
            gen.writeStringField("subGenus", sp.subGenus() != null ? sp.subGenus() : "");
            var cat = species_collection.getCat(sp.id());
            gen.writeStringField("orgcategory", cat);
            gen.writeStringField("category", group_classification.getGroupsKey(cat));
            gen.writeStringField("size", getSpNull(sp.size()));
            gen.writeStringField("depth", getSpNull(sp.depth()));
            gen.writeBooleanField("endemic", sp.endemic());
            gen.writeArrayFieldStart("distribution");
            for (String d : sp.dist()) gen.writeString(d);
            gen.writeEndArray();
            gen.writeArrayFieldStart("photos");
            for (Photo p : sp.photo()) {
                gen.writeStartObject();
                gen.writeNumberField("id", p.id());
                gen.writeStringField("location", getSpNull(p.location()));
                gen.writeStringField("type", getSpNull(p.type()));
                gen.writeStringField("comment", getSpNull(p.comment()));
                gen.writeEndObject();
            }
            gen.writeEndArray();
            gen.writeArrayFieldStart("thumbs");
            for (int t : sp.thumbs()) gen.writeNumber(t);
            gen.writeEndArray();
            gen.writeStringField("synonyms", getSpNull(sp.synonyms()));
            gen.writeStringField("aka", getSpNull(sp.aka()));
            gen.writeStringField("note", getSpNull(sp.note()));
            gen.writeArrayFieldStart("dispNames");
            if (sp.dispNames() != null) for (String n : sp.dispNames()) gen.writeString(n);
            gen.writeEndArray();
            gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.flush();
        writer.flush();
        writeToFile(writer.toString(), baseIndex + "/species_all.json");
    }

    private String getSpNull(String s) {
        return (s == null) ? "" : s;
    }

    private void genRSS(Page group, String baseIndex) throws IOException {
        if(!baseIndex.equals(basepathIndexAll)) {
            return;
        }
        StringBuilder outString = new StringBuilder();

        List<Species> sp_list = group.species;

        String buildDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).format(new Date());

        outString.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        outString.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
        outString.append("<channel>\n<title>Reefguide.org</title>\n");
        outString.append("<link>https://reefguide.org</link>");
        outString.append("<atom:link href=\"https://reefguide.org/reefguide.xml\" rel=\"self\" type=\"application/rss+xml\"/>");
        outString.append("<description>A Guide To The Tropical Reefs</description>");
        outString.append("<language>en-us</language>");
        outString.append("<lastBuildDate>").append(buildDate).append("</lastBuildDate>");

        AtomicInteger i = new AtomicInteger(0);
        sp_list.forEach(sp -> {
            String itemUrl = "https://reefguide.org/" + sp.id + ".html";
            outString.append("<item>");
            outString.append("<title>").append(sp.name).append("</title>");
            outString.append("<link>").append(itemUrl).append("</link>");
            outString.append("<guid isPermaLink=\"true\">").append(itemUrl).append("</guid>");
            outString.append("<description><![CDATA[");
            outString.append("<img src=\"https://reefguide.org/pix/thumb3/").append(sp.id).append(sp.thumbs.getFirst()).append(".jpg\" /><br />");
            outString.append(sp.name).append("<br />");
            if(sp.fullSciName() != null && !sp.fullSciName().isEmpty())
                outString.append("Scientific Name: <i>").append(sp.fullSciName()).append("</i><br />\n");
            var taxon = speciesTree.getPathToSpecies(sp.id);
            taxon.stream().filter(t -> t.getRank() != null && t.getRank().equals("Order")).findFirst().ifPresent(t -> {
                outString.append("Order: <i>").append(t.getName()).append("</i>");
                if(t.getCategory() != null) {
                    outString.append(" (").append(t.getCategory()).append(")");
                }
                outString.append("<br />\n");
            });
            taxon.stream().filter(t -> t.getRank() != null && t.getRank().equals("Family")).findFirst().filter(f -> !f.getName().equals("Unknown")).ifPresent(t -> {
                outString.append("Family: <i>").append(t.getName()).append("</i>");
                if(t.getCategory() != null) {
                    outString.append(" (").append(t.getCategory()).append(")");
                }
                outString.append("<br />\n");
            });

            outString.append("Category: ").append(species_collection.getCat(sp.id)).append("<br />\n");
            if(sp.size != null)
                outString.append("Size: ").append(sp.size).append("<br />\n");
            if(sp.depth != null)
                outString.append("Depth: ").append(sp.depth).append("<br />\n");
            outString.append("Distribution: ").append(String.join(", ", sp.dist)).append("<br />\n");
            outString.append("]]></description>");
            int idx = i.getAndIncrement();
            if(group.dates.get(idx) != null) {
                outString.append("<pubDate>").append(group.dates.get(idx)).append("</pubDate>");
            }
            outString.append("</item>");
        });
        outString.append("</channel></rss>");

        writeToFile(outString.toString(), baseIndex + "/reefguide.xml");

    }

    protected void genCatFile(List<Species> sp_list,
                              String baseIndex,
                              int reefRef,
                              String header, String cat) throws IOException {
        if(sp_list.isEmpty())
            return;
        String outString = readFile("index_cat.html");
        outString = processSelectedGuideMenu(outString, reefRef);
        StringBuilder img_reef = new StringBuilder();
        StringBuilder reef_name = new StringBuilder();
        StringBuilder sci_name = new StringBuilder();
        StringBuilder cat_reef = new StringBuilder();
        StringBuilder ref_reef = new StringBuilder();
        StringBuilder link_reef = new StringBuilder();
        String preName = "";
        if(!preReefName[reefRef].isEmpty()) {
            preName = "<span class=\"pretitle\">" + preReefName[reefRef] + "</span>";
        }
        String next = "";
        String prev = "";

        String base = "";
        if(!baseIndex.equals(basepathIndexAll)) {
            base = "../";
        }
        String subdir = "";
        int count1 = 0;
        for(var sp : sp_list) {
            for(var ph : sp.photo) {
                //node.indexLink = name;
                count1++;
                img_reef.append("\"").append(base).append("pix/thumb/").append(sp.id).append(ph.id).append(".jpg\",");
                link_reef.append("\"").append(sp.id).append(".html\",");
                reef_name.append("\"").append(sp.name);
                if(ph.type != null) {
                    reef_name.append(" - ").append(ph.type);
                }
                reef_name.append("\",");
                sci_name.append("\"").append(sp.fullSciName()).append("\",");
                if(!subdir.equals(cat)) {
                    subdir = cat;
                    cat_reef.append("\"").append(subdir).append("\",");
                    ref_reef.append(0).append(",");
            }
            }
        }
        ref_reef.append(count1);
        img_reef.deleteCharAt(img_reef.length() - 1);
        link_reef.deleteCharAt(link_reef.length() - 1);
        reef_name.deleteCharAt(reef_name.length() - 1);
        sci_name.deleteCharAt(sci_name.length() - 1);
        cat_reef.deleteCharAt(cat_reef.length() - 1);
        //ref_reef.deleteCharAt(ref_reef.length() - 1);
        if(ref_reef.toString().equals("0")) {
            ref_reef.append(",").append(sp_list.size());
        }


        outString = outString.replace("__IMG_REEF__", img_reef.toString());
        outString = outString.replace("__LINK_REEF__", link_reef.toString());
        outString = outString.replace("__NAME_REEF__", reef_name.toString());
        outString = outString.replace("__NAME_SCI__", sci_name.toString());
        outString = outString.replace("__CAT_REEF_", cat_reef.toString());
        outString = outString.replace("__REF_REEF__", ref_reef.toString());
        outString = outString.replace("__MAX_COL__", "0");
        outString = outString.replace("__PREV__", prev);

        outString = outString.replace("__NEXT__", next);
        outString = outString.replace("__REEF__", reefName[reefRef]);
        outString = outString.replace("__PRENAME__", preName);
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__REEFREF__", Integer.toString(reefRef));
        outString = outString.replace("__BANNER__", header);

        if(analytics) {
            outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
        } else {
            outString = outString.replace("__ANALYTICS__", "");
        }


        outString = outString.replace("__INDEX_HTML__", "");
        outString = outString.replace("__TITLE__", " - " + subdir + " - Show all");

        writeToFile(outString, baseIndex + "/" + cat.replace(' ', '_') + ".html");


    }

    static String[] fishFile = new String[numRegion];

    protected void genFishFile(Species sp, String baseIndex, int reefRef, String header, Page group) throws IOException {

        if(fishFile[reefRef] == null) {
            fishFile[reefRef] = readFile("species.html");
            fishFile[reefRef] = processSelectedGuideMenu(fishFile[reefRef], reefRef);
            fishFile[reefRef] = fishFile[reefRef].replace("__REEFREF__", Integer.toString(reefRef));
            fishFile[reefRef] = fishFile[reefRef].replace("__REEF__", reefName[reefRef]);
            if(analytics) {
                fishFile[reefRef] = fishFile[reefRef].replace("__ANALYTICS__", readFile("analytics.xml"));
            } else {
                fishFile[reefRef] = fishFile[reefRef].replace("__ANALYTICS__", "");
            }

        }
        String outString = fishFile[reefRef];

        StringBuilder output = new StringBuilder();
        StringBuilder text = new StringBuilder();
        String base = "";
        if(!baseIndex.equals(basepathIndexAll)) {
            base = "../";
        }
        String prename = "";
        if(!preReefName[reefRef].isEmpty()) {
            prename = "<span class=\"pretitle\">" + preReefName[reefRef] + "</span>";
        }

        outString = outString.replace("__PRENAME__", prename);

        outString = outString.replace("__GROUP__", group.name);

        outString = outString.replace("__NAME2__", sp.name);
        String actualName = sp.name;
        String actualSciName = sp.fullSciName();
        if(sp.name.equals(sp.sciName)) {
            actualName = "<i>" + sp.fullSciName() + "</i>";
            actualSciName = "";
        }
        if((sp.photo.size() == 1) && (sp.photo.getFirst().type != null)) {
            outString = outString.replace("__NAME__", actualName + " - " + sp.photo.getFirst().type);
        } else {
            outString = outString.replace("__NAME__", actualName);
        }
        if(!sp.sciName().isEmpty()) {
            outString = outString.replace("__SCINAME__", "<span class=\"details\">Scientific Name: </span><span class=\"sntitle\">" + sp.fullSciName() + "</span>");
            outString = outString.replace("__SCINAME2__", actualSciName);
        } else {
            outString = outString.replace("__SCINAME__", "");
            outString = outString.replace("__SCINAME2__", "");
        }
        if(!sp.synonyms.isBlank()) {
            outString = outString.replace("__ASCINAME__", "<span class=\"details\">Synonyms: </span><span class=\"sntitle2\">" + sp.synonyms + "</span>");
        } else {
            outString = outString.replace("__ASCINAME__", "");
        }

        var cat = species_collection.getCat(sp.id());

        outString = outString.replace("__TITLE__", sp.name + " - " + sp.fullSciName() + " - " + cat + " - " + sp.aka);

        StringBuilder taxonomy = new StringBuilder();
        List<String> remove = Arrays.asList("Domain", "Kingdom");
        String name = sp.fullSciName();
        if(name.isEmpty()) {
            name = sp.name();
        }
        StringBuilder ident = new StringBuilder();
        speciesTree.getPathToSpecies(name).stream().filter(t -> !t.getShortSciName().contains("undescribed")).filter(t -> !t.getShortSciName().contains("/")).
                filter(t -> !remove.contains(t.getRank())).forEach(t -> {
                    var tip = "";
                    if(t.getCategory() != null) {
                        tip = " title=\"" + t.getCategory().replace("&", "&amp;") + "\"";
                    }
                    var taxonUrl = "index" + group.index + ".html#taxon=" + URLEncoder.encode(t.getShortSciName(), StandardCharsets.UTF_8);
                    taxonomy.append("<div class=\"infodetails\"><span class=\"sntitle\"").append(tip).append(">").append(ident)
                            .append("<a href=\"").append(taxonUrl).append("\" class=\"taxonlink\">")
                            .append(t.getShortSciName()).append("</a>")
                            .append("</span><span class=\"details\"> (").append(t.getRank()).append(")</span></div>").append("\n");
                    if(ident.isEmpty())
                        ident.append("&boxur;");
                    else
                        ident.insert(0, "&nbsp;");
                });
        outString = outString.replace("__HIGHER__", taxonomy.toString());

        outString = outString.replace("__CAT__", cat);
        outString = outString.replace("__CAT1__", cat.replace(" ", "_"));
        outString = outString.replace("__MAINCAT__", cat);
        outString = outString.replace("__DIST1__", String.join(", ", sp.dist) + " - " + sp.aka);

        outString = outString.replace("__DIST__", "<span class=\"details\">Distribution: </span><span class=\"details2\">" + String.join(", ", sp.dist)
                + (sp.endemic ? " (Endemic)" : "") + "</span>");

        if(!sp.aka.isEmpty()) {
            outString = outString.replace("__AKA__", "<span class=\"details\">Also known as: </span><span class=\"details2\">" + sp.aka + "</span>");
        } else {
            outString = outString.replace("__AKA__", "");
        }
        if(sp.note != null) {
            outString = outString.replace("__NOTE__", "<span class=\"details\">"  + processNote(sp.note) + "</span><br />");
        } else {
            outString = outString.replace("__NOTE__", "");
        }

        outString = outString.replace("__INDEX__", "index" + group.index + ".html");
        outString = outString.replace("__BANNER__", header);
        outString = outString.replace("__BASE__", base);


        if(sp.size != null) {
            text.append("<span class=\"details\">Size: </span><span class=\"details2\">");
            text.append(sp.size);
            text.append("</span>&nbsp;&nbsp;");
        }
        outString = outString.replace("__SIZE__", text.toString());
        text = new StringBuilder();
        if(sp.depth != null) {
            text.append("<span class=\"details\">Depth: </span><span class=\"details2\">");
            text.append(sp.depth);
            text.append("</span>");
        }
        outString = outString.replace("__DEPTH__", text.toString());

        for(var ph : sp.photo) {
            String thumbimg = sp.id + ph.id;

            output.append("<div class=\"galleryspan\">\n");
            if(sp.photo.size() > 1) {
                output.append("<a class=\"pixsel\" href=\"pixhtml/").append(thumbimg).append(".html\">");
                String title = sp.name + " - " + sp.fullSciName() + " - " + ph.location;
                output.append("<img class=\"selframe\" src=\"").append(base).append("pix/thumb2/").append(thumbimg).append(".jpg\" alt=\"").append(title).append("\" title=\"").append(title).append("\"/></a>\n");
                output.append(" <div class=\"main2\">").append(ph.location).append("</div>\n");
                String comment = (ph.type == null ? "" : ph.type)
                        + ((ph.comment != null && ph.type != null) ? " - " : "")
                        + (ph.comment == null ? "&nbsp" : ph.comment);
                output.append(" <div class=\"main3\">").append(comment).append("</div>\n");

            } else {
                String title = sp.name + " - " + sp.fullSciName() + " - " + ph.location;
                output.append("<img class=\"selframe\" src=\"").append(base).append("pix/").append(thumbimg).append(".jpg\" alt=\"").append(title).append("\" title=\"").append(title).append("\"/></a>\n");
                output.append("<div>");
                String div = "";
                if(ph.comment != null) {
                    output.append(ph.comment);
                    div = " / ";
                }
                if(!ph.location.isEmpty()) {
                    output.append(div).append("Location: ").append(ph.location);
                }
                output.append("</div>");
            }
            output.append("</div>\n");
            genFishPixFile(sp, ph, cat, baseIndex, reefRef, header);
        }

        outString = outString.replace("__FISH_HTML__", output.toString());

        var node = speciesTree.findSpecies(sp.id);
        if(node.AphiaID != 0 || node.iNaturalistID != 0) {
            var links =  new StringBuilder();
            links.append("<div class=\"infobox\"><div class=\"worms\">\n");
            if(node.AphiaID != 0) {
                links.append("<a href=\"https://marinespecies.org/aphia.php?p=taxdetails&id=").append(node.AphiaID).append("\" target=\"_blank\">WoRMS ID: ").
                        append(node.AphiaID).append(Character.toChars(0x1F517)).append("</a>");
            }
            if(node.iNaturalistID != 0) {
                if(node.AphiaID != 0) {
                    links.append("<br />\n");
                }
                links.append("<a href=\"https://www.inaturalist.org/taxa/").append(node.iNaturalistID).append("\" target=\"_blank\">iNaturalist ID: ").
                        append(node.iNaturalistID).append(Character.toChars(0x1F517)).append("</a>");
            }
            links.append("</div></div>\n");
            outString = outString.replace("__LINKS__", links.toString());
        } else {
            outString = outString.replace("__LINKS__", "");
        }



        writeToFile(outString, baseIndex + "/" + sp.id + ".html");
    }

    static String[] pixFile = new String[numRegion];

    protected void genFishPixFile(Species sp, Photo ph, String cat, String baseIndex, int reefRef, String banner) throws IOException {
        numPhotos++;
        String outString;
        if(pixFile[reefRef] == null) {
            pixFile[reefRef] = readFile("singlephoto.html");
            pixFile[reefRef] = processSelectedGuideMenu(pixFile[reefRef], reefRef);
            pixFile[reefRef] = pixFile[reefRef].replace("__REEFREF__", Integer.toString(reefRef));
            pixFile[reefRef] = pixFile[reefRef].replace("__REEF__", reefName[reefRef]);
            pixFile[reefRef] = pixFile[reefRef].replace("__PRENAME__", preReefName[reefRef]);
            if(analytics) {
                pixFile[reefRef] = pixFile[reefRef].replace("__ANALYTICS__", readFile("analytics.xml"));
            } else {
                pixFile[reefRef] = pixFile[reefRef].replace("__ANALYTICS__", "");
            }
        }
        outString = pixFile[reefRef];

        String base = "";
        if(!baseIndex.equals(basepathIndexAll)) {
            base = "../";
        }
        outString = outString.replace("__BANNER__", banner);
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__TITLE__", sp.name + " - " + sp.fullSciName() + " - " + ph.location + " - Photo " + ph.id);

        String actualName = sp.name;
        String actualSciName = sp.fullSciName();
        if(sp.name.equals(sp.sciName)) {
            actualName = "<i>" + sp.fullSciName() + "</i>";
            actualSciName = "";
        }

        if(ph.type != null) {
            outString = outString.replace("__NAME__", actualName + " - " + ph.type);
        } else {
            outString = outString.replace("__NAME__",actualName);
        }
        outString = outString.replace("__NAME2__", sp.name);

        if(!sp.sciName.isEmpty()) {
            outString = outString.replace("__SCINAME__", "<span class=\"details\">Scientific Name: </span><span class=\"sntitle\">" + sp.fullSciName() + "</span>");
            outString = outString.replace("__SCINAME2__", actualSciName);
        } else {
            outString = outString.replace("__SCINAME__", "");
            outString = outString.replace("__SCINAME2__", "");
        }

        var dist = String.join(", ", sp.dist) + (sp.endemic ? " (Endemic)" : "");
        outString = outString.replace("__CAT__", cat);
        //outString = outString.replace("__MAINCAT__", node.cat);
        outString = outString.replace("__DIST1__", dist + " - " + sp.aka);
        outString = outString.replace("__DIST__", "<span class=\"details\">Distribution: </span><span class=\"details2\">" + dist + "</span>");

        if(!sp.aka.isEmpty()) {
            outString = outString.replace("__AKA__", "<span class=\"details\">Also known as: </span><span class=\"details2\">" + sp.aka + "</span>");
        } else {
            outString = outString.replace("__AKA__", "");
        }
        StringBuilder text = new StringBuilder();
        if(sp.size != null) {
            text.append("<span class=\"details\">Size: </span><span class=\"details2\">");
            text.append(sp.size);
            text.append("</span>&nbsp;&nbsp;");
        }
        outString = outString.replace("__SIZE__", text.toString());
        text = new StringBuilder();
        if(sp.depth != null) {
            text.append("<span class=\"details\">Depth: </span><span class=\"details2\">");
            text.append(sp.depth);
            text.append("</span>");
        }
        outString = outString.replace("__DEPTH__", text.toString());

        StringBuilder thumblist_before = new StringBuilder();
        StringBuilder thumblist_after = new StringBuilder();
        boolean before = true;
        for(var sidePhoto : sp.photo) {
            StringBuilder thumblist = new StringBuilder();
            String thumb = sp.id + sidePhoto.id;
            thumblist.append("<div class=\"infoimg\">");
            //if(i == index) {
            if(ph.id == sidePhoto.id) {
                thumblist.append("<img src=\"").append(base).append("../pix/thumb3/").append(thumb).append(".jpg\" />\n");
                before = false;
            } else {
                thumblist.append("<a href=\"").append(thumb).append(".html\"><img src=\"").append(base).append("../pix/thumb3/").append(thumb).append(".jpg\" /></a>\n");
            }
            thumblist.append("<div>");
            thumblist.append("&nbsp;");

            thumblist.append("</div>");
            thumblist.append("</div>");
            if(before)
                thumblist_before.append(thumblist);
            else
                thumblist_after.append(thumblist);
        }
        outString = outString.replace("__THUMBS__", thumblist_after.append(thumblist_before).toString());


        String thumbimg = sp.id + ph.id;
        StringBuilder output = new StringBuilder();
        output.append("<br /><img class=\"selframe\" src=\"").append(base).append("../pix/").append(thumbimg).append(".jpg\" alt=\"").append(sp.name).append(" - ").append(sp.fullSciName()).append("\" title=\"").append(sp.name).append(" - ").append(sp.fullSciName()).append("\" />\n");
        output.append("<div>");
        String div = "";
        if(ph.comment != null) {
            output.append(ph.comment);
            div = " / ";
        }
        if(ph.location == null) {
            System.out.println("Null Location for " + sp.id);
            System.out.flush();
        }
        if(!Objects.requireNonNull(ph.location).isBlank()) {
            output.append(div).append("Location: ").append(ph.location);
        }
        output.append("</div><br />");

        outString = outString.replace("__FISH_HTML__", output.toString());

        outString = outString.replace("__INDEX__", "../" + sp.id + ".html");


        writeToFile(outString, baseIndex + "/pixhtml/" + thumbimg + ".html");

    }

    protected String processNote(String note) {
        StringBuilder ret = new StringBuilder();
        String[] lines = note.split("<br />");
        ret.append(lines[0]);
        for(int i = 1; i < lines.length; i++) {
            ret.append(lines[i]);
        }
        return ret.toString();

    }


    protected String processSelectedGuideMenu(String outString, int reefRef) {

        if(reefRef == 0)
            outString = outString.replace("__CHECK_ALL__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[0]);
        else
            outString = outString.replace("__CHECK_ALL__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[0]);
        if(reefRef == 1)
            outString = outString.replace("__CHECK_CAR__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[1]);
        else
            outString = outString.replace("__CHECK_CAR__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[1]);
        if(reefRef == 2)
            outString = outString.replace("__CHECK_PAC__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[2]);
        else
            outString = outString.replace("__CHECK_PAC__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[2]);
        if(reefRef == 3)
            outString = outString.replace("__CHECK_KEY__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[3]);
        else
            outString = outString.replace("__CHECK_KEY__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[3]);
        if(reefRef == 4)
            outString = outString.replace("__CHECK_HAW__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[4]);
        else
            outString = outString.replace("__CHECK_HAW__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[4]);
        if(reefRef == 5)
            outString = outString.replace("__CHECK_EPAC__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[5]);
        else
            outString = outString.replace("__CHECK_EPAC__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[5]);
        if(reefRef == 6)
            outString = outString.replace("__CHECK_FP__", "<div class=\"ui-icon ui-icon-check arrow\"></div>" + reefMenu[6]);
        else
            outString = outString.replace("__CHECK_FP__", "<div class=\"arrow2\">&nbsp;</div>" + reefMenu[6]);

        return outString;

    }

    protected void writeToFile(String outString, String filename) throws IOException {
        boolean sameFile = compareToFile(outString, filename);
        if(!sameFile) {
            try(java.io.BufferedWriter outFile = new java.io.BufferedWriter(new java.io.FileWriter(filename))) {
                outFile.write(outString);
            }
            System.out.println(filename);
        }

    }

    protected void genIndexFile(
            String baseIndex,
            Page g,
            int reefRef,
            String header) throws IOException {
        String indexName;
        if(g.index == -1)
            indexName = "latest.html";
        else {
            indexName = "index" + g.index + ".html";
        }
        String preReefString = "";
        if(!preReefName[reefRef].isEmpty()) {
            preReefString = "<span class=\"pretitle\">" + preReefName[reefRef] + "</span>";
        }
        String base = "";
        if(!baseIndex.equals(basepathIndexAll)) {
            base = "../";
        }
        String outString;

        outString = readFile("index.html");

        if(g.index == -1)
            outString = outString.replace("__HEADLINE__", "<div style=\"margin: auto; width: 100%; text-align: center;color: #dcd637;font-size:24pt;padding:10px;\">Latest Updates</div>");
        else
            outString = outString.replace("__HEADLINE__", "");

        outString = processSelectedGuideMenu(outString, reefRef);

        StringBuilder img_reef = new StringBuilder();
        StringBuilder reef_name = new StringBuilder();
        StringBuilder sci_name = new StringBuilder();
        StringBuilder cat_reef = new StringBuilder();
        StringBuilder ref_reef = new StringBuilder();
        StringBuilder link_reef = new StringBuilder();
        StringBuilder title = new StringBuilder();
        String subdir = "";
        int extra = 0;

        int i = 0;
        for(var sp : g.species) {
            if(sp == null) {
                System.out.println("Null species in " + g.name);
                continue;
            }
            int num = sp.getNameCount();
            for(int j = 0; j < num; j++) {
                img_reef.append("\"").append(base).append("pix/thumb/").append(sp.id).append(sp.thumbs().get(j)).append(".jpg\",");
                link_reef.append("\"").append(sp.id).append(".html\",");
                //reef_name.append("\"").append(node.getName(j)).append(zxc).append("\",");
                reef_name.append("\"").append(sp.getDispName(j)).append("\",");
                sci_name.append("\"").append(sp.fullSciName()).append("\",");
            }
            var cat = species_collection.getCat(sp.id());
            if(!subdir.equals(cat)) {
                subdir = cat;
                cat_reef.append("\"").append(subdir).append("\",");
                ref_reef.append(i + extra).append(",");
            }
            i++;
            extra += (num - 1);

        }
        ref_reef.append(extra + g.species.size());
        if(!img_reef.isEmpty()) {
            img_reef.deleteCharAt(img_reef.length() - 1);
            link_reef.deleteCharAt(link_reef.length() - 1);
            reef_name.deleteCharAt(reef_name.length() - 1);
            sci_name.deleteCharAt(sci_name.length() - 1);
            cat_reef.deleteCharAt(cat_reef.length() - 1);
        }
        if(ref_reef.toString().equals("0")) {
            ref_reef.append(",").append(g.species.size());
        }
        outString = outString.replace("__REEFREF__", Integer.toString(reefRef));
        outString = outString.replace("__IMG_REEF__", img_reef.toString());
        outString = outString.replace("__LINK_REEF__", link_reef.toString());
        outString = outString.replace("__NAME_REEF__", reef_name.toString());
        outString = outString.replace("__NAME_SCI__", sci_name.toString());
        outString = outString.replace("__CAT_REEF_", cat_reef.toString());
        outString = outString.replace("__REF_REEF__", ref_reef.toString());
        outString = outString.replace("__MAX_COL__", "0");
        outString = outString.replace("__PREVNAME__", "");
        outString = outString.replace("__NEXTNAME__", "");
        outString = outString.replace("__REEF__", reefName[reefRef]);
        outString = outString.replace("__PRENAME__", preReefString);
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__BANNER__", header);
        if(analytics) {
            outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
        } else {
            outString = outString.replace("__ANALYTICS__", "");
        }

        String treeMenuJson = buildTreeMenuJson(indexName);
        outString = outString.replace("__TREEMENU_JSON__", treeMenuJson);

        List<SpeciesTree.TreeNode<SpeciesTree.Taxon>> speciesTreeNodes = new ArrayList<>();
        for(var sp : g.species) {
            if (sp == null) continue;
            var speciesNode = speciesTree.findSpecies(sp.id());
            if (speciesNode != null) {
                var treeNode = speciesTree.depthFirstSearch(speciesNode.getName());
                if (treeNode != null) {
                    speciesTreeNodes.add(treeNode);
                }
            }
        }
        String lcaName = "";
        if(!speciesTreeNodes.isEmpty()) {
            if(speciesTreeNodes.size() == 1) {
                lcaName = speciesTreeNodes.getFirst().getValue().getName();
            } else {
                var lca = speciesTree.findLowestCommonAncestor(speciesTreeNodes);
                if(lca != null) {
                    if("Tunicates".equals(speciesTreeNodes.getFirst().getValue().getCategory())) {
                        lcaName = "Tunicata";
                    } else {
                        if(lca.getValue().getRank().equals("Kingdom") || lca.getValue().getRank().equals("Domain")) {
                            var first = speciesTreeNodes.getFirst();
                            while(first != null && (!first.getValue().getRank().equals("Phylum"))) {
                                first = first.getParent();
                            }
                            lcaName = first != null ? first.getValue().getName() : lca.getValue().getName();
                        } else {
                            lcaName = lca.getValue().getName();
                        }
                    }
                }
            }
        }
        outString = outString.replace("__LCA_NAME__", lcaName);

        StringBuilder html = new StringBuilder();
        subdir = "";
        int col = 0;
        for(var sp : g.species) {
            var cat = species_collection.getCat(sp.id());
            if(!subdir.equals(cat)) {
                if(!subdir.isEmpty()) {
                    html.append("</div>\n");
                }
                subdir = cat;
                html.append("<div class=\"catheader\"><a href=\"").append(subdir.replace(" ", "_")).append(".html\">").append(subdir).append("</a></div>\n");
                html.append("<div class=\"grid-row\" style=\"grid-template-columns: repeat(3, 1fr)\">");
                col = 0;
                title.append(" - ").append(subdir);
            }
            if((col != 0) && ((col % 3) == 0)) {
                html.append("</div>\n<div class=\"grid-row\" style=\"grid-template-columns: repeat(3, 1fr)\">");
            }
            html.append("<div class=\"celltd\" style=\"flex-direction: column\"><img src=\"").append(base).append("pix/thumb/").append(sp.id).append(sp.thumbs().getFirst()).append(".jpg\" alt=\"").append(sp.name).append(" - ").append(sp.fullSciName()).append("\" title=\"").append(sp.name).append(" - ").append(sp.fullSciName()).append("\" />\n");
            html.append("<div class=\"nameid\"><a href=\"").append(sp.id).append(".html\">").append(sp.name).append("</a></div></div>");
            col++;
        }
        html.append("</div>\n");

        outString = outString.replace("__INDEX_HTML__", html.toString());
        outString = outString.replace("__TITLE__", title.toString());

        // activeSel is now embedded in treeMenuData JSON

        writeToFile(outString, baseIndex + "/" + indexName);

        // Generate taxonomy-only index0.html once, using the first page's data
        if (g.index != 1) return;
        String out0 = readFile("index0.html");
        out0 = processSelectedGuideMenu(out0, reefRef);
        out0 = out0.replace("__HEADLINE__", "");
        out0 = out0.replace("__REEFREF__", Integer.toString(reefRef));
        out0 = out0.replace("__IMG_REEF__", img_reef.toString());
        out0 = out0.replace("__LINK_REEF__", link_reef.toString());
        out0 = out0.replace("__NAME_REEF__", reef_name.toString());
        out0 = out0.replace("__NAME_SCI__", sci_name.toString());
        out0 = out0.replace("__CAT_REEF_", cat_reef.toString());
        out0 = out0.replace("__REF_REEF__", ref_reef.toString());
        out0 = out0.replace("__MAX_COL__", "0");
        out0 = out0.replace("__PREVNAME__", "");
        out0 = out0.replace("__NEXTNAME__", "");
        out0 = out0.replace("__REEF__", reefName[reefRef]);
        out0 = out0.replace("__PRENAME__", preReefString);
        out0 = out0.replace("__BASE__", base);
        out0 = out0.replace("__BANNER__", header);
        out0 = out0.replace("__ANALYTICS__", analytics ? readFile("analytics.xml") : "");
        out0 = out0.replace("__TREEMENU_JSON__", treeMenuJson);
        out0 = out0.replace("__LCA_NAME__", "None");
        out0 = out0.replace("__TITLE__", title.toString());
        writeToFile(out0, baseIndex + "/index0.html");
    }

    protected void genCatalogFiles(Collection<Species> sp_list,
                                   String baseIndex, int reefRef, String header) throws IOException {

        TreeMap<String, Species> catName = new TreeMap<>();
        TreeMap<String, Species> sciName = new TreeMap<>();
        TreeMap<String, TreeMap<String, Species>> grpName = new TreeMap<>();

        String base = "";
        if(!baseIndex.equals(basepathIndexAll)) {
            base = "../";
        }

        String preReefString = "";
        if(!preReefName[reefRef].isEmpty()) {
            preReefString = "<span class=\"pretitle\">" + preReefName[reefRef] + "</span>";
        }

        String outString = readFile("index_catalog.html");

        outString = processSelectedGuideMenu(outString, reefRef);
        outString = outString.replace("__REEFREF__", Integer.toString(reefRef));
        outString = outString.replace("__BANNER__", header);

        sp_list.forEach(sp -> {
            catName.put(sp.name, sp);
            if(!sp.sciName.isEmpty()) {
                sciName.put(sp.fullSciName(), sp);
            }
            if(!sp.aka.isEmpty()) {
                String[] akas = sp.aka.split(",");
                for(String aka : akas) {
                    catName.put(aka.trim() + " [" + sp.name + "]", sp);
                }
            }
            var cat = species_collection.getCat(sp.id());
            if(!grpName.containsKey(cat)) {
                grpName.put(cat, new TreeMap<>());
            }
            grpName.get(cat).put(sp.name, sp);
        });

        StringBuilder html = new StringBuilder();
        char alpha = '.';
        int split = catName.size() / 2;
        for(String name : catName.keySet()) {
            Species sp = catName.get(name);
            if(name.charAt(0) != alpha) {
                if(split <= 0) {
                    html.append("\n</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td>\n");
                    split = catName.size(); // / ;
                }
                alpha = name.charAt(0);
                html.append("<div class=\"bigalpha\">").append(alpha).append("</div>");
            }
            html.append("<a class=\"tocname\" href=\"").append(sp.id).append(".html\">").append(name).append("</a><br />");
            split--;
        }
        outString = outString.replace("__HTML__", html.toString());
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__REEF__", reefName[reefRef]);
        outString = outString.replace("__PRENAME__", preReefString);
        outString = outString.replace("__TITLE__", " - Index of Species by Common Names");
        if(analytics) {
            outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
        } else {
            outString = outString.replace("__ANALYTICS__", "");
        }

        outString = outString.replace("__LINKS__", "By Common Name | <a class=\"catalog\" href=\"cat_grp.html\">By Category</a> | <a class=\"catalog\" href=\"cat_sci.html\">By Scientific Names</a>");

        writeToFile(outString, baseIndex + "/cat.html");


        outString = readFile("index_catalog.html");
        outString = processSelectedGuideMenu(outString, reefRef);
        outString = outString.replace("__REEFREF__", Integer.toString(reefRef));
        outString = outString.replace("__BANNER__", header);
        html = new StringBuilder();
        alpha = '.';
        String first = "";
        split = sciName.size() / 3; // - 0
        for(Species sp : sciName.values()) {
            if(sp.sciName().charAt(0) != alpha) {
                if(split <= 0) {
                    html.append("\n</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td>\n");
                    split = sciName.size() / 3; // - 0
                }
                alpha = sp.sciName().charAt(0);
                html.append("<div class=\"bigalpha\">").append(alpha).append("</div>");
            }
            if(sp.sciName().split(" ")[0].equals(first)) {
                html.append("<a class=\"tocnamesci\" href=\"").append(sp.id).append(".html\">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;").append(sp.sciName().split(" ")[1]);
                if(sp.sciName().split(" ").length > 2)
                    html.append(" ").append(sp.sciName().split(" ")[2]);
                html.append("</a><br />");
            } else {
                html.append("<a class=\"tocnamesci\" href=\"").append(sp.id).append(".html\">").append(sp.sciName()).append("</a><br />");
                first = sp.sciName().split(" ")[0];
            }
            split--;
        }
        outString = outString.replace("__HTML__", html.toString());
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__REEF__", reefName[reefRef]);
        outString = outString.replace("__PRENAME__", preReefString);
        outString = outString.replace("__TITLE__", " - Index of Species by Scientific Names");
        if(analytics) {
            outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
        } else {
            outString = outString.replace("__ANALYTICS__", "");
        }
        outString = outString.replace("__LINKS__", "<a class=\"catalog\" href=\"cat.html\">By Common Name</a> | <a class=\"catalog\" href=\"cat_grp.html\">By Category</a> | By Scientific Names");

        writeToFile(outString, baseIndex + "/cat_sci.html");


        outString = readFile("index_catalog.html");
        outString = processSelectedGuideMenu(outString, reefRef);
        outString = outString.replace("__REEFREF__", Integer.toString(reefRef));
        outString = outString.replace("__BANNER__", header);
        html = new StringBuilder();
        String curgrp = "";
        split = species_collection.getAllSpecies().size() / 3;
        for(String elem : grpName.keySet()) {
            if(!elem.equals(curgrp)) {
                if(split <= 0) {
                    html.append("\n</td><td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td><td>\n");
                    split = species_collection.getAllSpecies().size() / 3;
                }
                curgrp = elem;
                html.append("<div class=\"biggrp\">").append(curgrp).append("</div>");
            }
            TreeMap<String, Species> nameTree = grpName.get(elem);
            for(Species sp : nameTree.values()) {
                split--;
                html.append("<a class=\"tocnamegrp\" href=\"").append(sp.id).append(".html\">").append(sp.name).append("</a><br />");
            }

        }
        outString = outString.replace("__HTML__", html.toString());
        outString = outString.replace("__REEF__", reefName[reefRef]);
        outString = outString.replace("__PRENAME__", preReefString);
        outString = outString.replace("__BASE__", base);
        outString = outString.replace("__TITLE__", " - Index of Species by Categories");
        if(analytics) {
            outString = outString.replace("__ANALYTICS__", readFile("analytics.xml"));
        } else {
            outString = outString.replace("__ANALYTICS__", "");
        }

        outString = outString.replace("__LINKS__", "<a class=\"catalog\" href=\"cat.html\">By Common Name</a> |By Category | <a class=\"catalog\" href=\"cat_sci.html\">By Scientific Names</a>");

        writeToFile(outString, baseIndex + "/cat_grp.html");

    }

    static final String[] typeList = new String[]{"Fish", "Invertebrates", "Sponges", "Corals", "Algae", "Marine Reptiles &amp; Mammals"};

    private String getSpeciesClass(String cat) {
        String type = group_classification.getCatSpeciesType(cat);
        if(type == null) {
            return null;
        }
        return switch(type) {
            case "Mammals" -> "Marine Reptiles &amp; Mammals";
            default -> type;
        };
    }

    private boolean isSingleList(String speciesClass) {
        return !speciesClass.equals(typeList[0]) && !speciesClass.equals(typeList[1]);
    }

    private String buildTreeMenuJson(String name) {
        StringBuilder json = new StringBuilder();
        json.append("{\"sections\":[");

        String speciesClass = "";
        boolean singleClass = false;
        int ul_fam_open_counter = 0;
        int ul_fam_open = 0;
        int activeSection = -1;
        int sectionIndex = -1;
        boolean firstSection = true;
        boolean firstFamily = true;
        boolean firstPage = true;

        for (Page elem : pageList) {
            if (elem.species.isEmpty())
                continue;

            String currentClass = getSpeciesClass(species_collection.getCat(elem.species.getFirst().id));

            if (!Objects.equals(currentClass, speciesClass)) {
                // Close previous family and section
                if (!speciesClass.isEmpty()) {
                    json.append("]}]}");  // close pages array + family, families array + section
                }
                speciesClass = currentClass;
                singleClass = isSingleList(Objects.requireNonNull(speciesClass));
                sectionIndex++;
                firstFamily = true;

                if (!firstSection) json.append(",");
                firstSection = false;
                json.append("{\"name\":").append(jsonString(speciesClass)).append(",\"families\":[");
            }

            if (elem.page == 1) {
                // Close previous family
                if (!firstFamily) {
                    json.append("]},");  // close pages array, family
                } else {
                    firstFamily = false;
                }
                ul_fam_open_counter++;
                firstPage = true;

                json.append("{\"name\":").append(jsonString(elem.name));
                json.append(",\"single\":").append(singleClass);
                // open flag will be set after we know which family is active
                json.append(",\"familyIndex\":").append(ul_fam_open_counter);
                json.append(",\"pages\":[");
            }

            boolean active = name.equals("index" + elem.index + ".html");
            if (active) {
                ul_fam_open = ul_fam_open_counter;
                activeSection = sectionIndex;
            }

            if (!firstPage) json.append(",");
            firstPage = false;

            json.append("{\"href\":\"index").append(elem.index).append(".html\"");
            json.append(",\"active\":").append(active);
            json.append(",\"categories\":[");

            String prev = "";
            boolean firstCat = true;
            for (Species sp : elem.species) {
                var cat = species_collection.getCat(sp.id());
                cat = elem.group.get(cat);
                if (prev == null) continue;
                if (!prev.equals(cat)) {
                    prev = cat;
                    if (!firstCat) json.append(",");
                    firstCat = false;
                    json.append(jsonString(prev));
                }
            }
            json.append("]}");
        }

        // Close last family and section
        if (!speciesClass.isEmpty()) {
            json.append("]}]}");
        }

        json.append("],\"activeSection\":").append(activeSection).append("}");

        // Post-process: set open flag on the active family
        String result = json.toString();
        if (ul_fam_open > 0) {
            result = result.replace("\"familyIndex\":" + ul_fam_open + ",", "\"open\":true,");
        }
        result = result.replaceAll("\"familyIndex\":\\d+,", "\"open\":false,");

        return result;
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        // Unescape HTML entities before encoding as JSON
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'");
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }


    protected String readFile(String name) throws IOException {
        byte[] b;
        try(java.io.InputStream fis = getClass().getResourceAsStream(name)) {
            b = new byte[Objects.requireNonNull(fis).available()];
            if(fis.read(b) != b.length)
                throw new IOException();
        }
        return new String(b);
    }

    static protected boolean compareToFile(String fileString, String fileName) {
        String fileContent;
        try {
            fileContent = Files.readString(Paths.get(fileName));
            return fileContent.equals(fileString);
        } catch(IOException e) {
            return false;
        }
    }

    static void main(String... args) throws Exception {

        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "warn");
        //Thread.sleep(10000);
        GenReef4 reef = new GenReef4();
        reef.basepathIndexAll = "/data5/reef41";
        if(args.length == 1) {
            reef.basepathIndexAll = args[0];
        }

        reef.createSite(reef.basepathIndexAll, true);
        reef.closeMongoDB();
    }

    private void copyFile(String source, String dest) throws IOException {

        try(FileChannel in = new FileInputStream(source).getChannel()) {
            try(FileChannel out = new FileOutputStream(dest).getChannel()) {
                long size = in.size();
                MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
                out.write(buf);
            }
        } catch(Exception fnfe) {
            fnfe.printStackTrace();
        }
    }
}
