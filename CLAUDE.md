# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build fat JAR (bundles all dependencies)
./gradlew assemble

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests SpeciesTreeTest

# Run a specific test method
./gradlew test --tests "SpeciesTreeTest.testDepthFirstSearch"

# Full build including tests
./gradlew build
```

## Architecture Overview

This is **Florent's Guide to the Tropical Reefs** — a reef species catalog system with three layers:

### Core Data Model (`SpeciesTree.java`)
A hierarchical taxonomic tree supporting 25 ranks (Domain → Kingdom → Phylum → ... → Species). Key types:
- `Taxon` — a node in the taxonomy tree (any rank)
- `Species extends Taxon` — leaf node with scientific name, common name, AphiaID (WoRMS), genus/epithet/subgenus
- `TreeNode<T>` — generic tree node wrapping Taxon/Species with parent/children

`SpeciesTree` builds the tree from MongoDB and provides DFS/BFS traversal, category lookup, path-to-species, and CSV export.

### Application Controller (`genReef4.java`)
Main class (`us.florent.genReef35` per the JAR manifest). Handles:
- MongoDB I/O against the `reef4` database, collections: `taxon` and `species`
- Species data as a record type with photos, distribution, depth, size, synonyms
- WoRMS REST API integration (parallel, via ForkJoinPool) to fetch AphiaIDs and validate taxonomy
- Web page/JSON generation for the frontend
- `genusClassification` inner class managing category groupings

### GUI (`speciesEdit.java`)
Swing desktop app for editing species metadata (distributions, photos, synonyms). Uses `ListDialog` and `distDialog` for selection UIs.

### Web Frontend
React SPA (migrated from AngularJS + jQuery) with HTML templates (`home.html`, `species.html`, `search.html`, etc.) and React components in `src/main/js/` (`AccordionMenu.js`, `SpeciesSearch.js`, `TopNav.js`, `dynPage.js`, `mainindex.js`). Served statically; data comes from generated JSON/HTML files.

## Key Design Notes

- `SpeciesTree` and `genReef4` each define their own `Species` type — the `SpeciesTree.Species` models taxonomy identity, while `genReef4.Species` (a record) holds catalog data (photos, distribution, etc.).
- WoRMS integration uses a custom `ForkJoinPool` for parallel REST calls; results are reconciled against the local taxonomy tree.
- MongoDB aggregation pipelines are used for rank-based sorting of species.
- The fat JAR includes all runtime dependencies via `configurations.runtimeClasspath`.
