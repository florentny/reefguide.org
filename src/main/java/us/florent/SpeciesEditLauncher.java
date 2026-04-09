package us.florent;

public class SpeciesEditLauncher {
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "warn");
        SpeciesEdit.main(args);
    }
}
