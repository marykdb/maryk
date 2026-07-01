# Maryk IndexedDB Demo

Separate browser demo app for the local Maryk IndexedDB store.

Run:

```bash
./gradlew jsBrowserDevelopmentRun
```

Build:

```bash
./gradlew jsBrowserDistribution
```

The app opens a real browser IndexedDB database, writes Maryk models, reads them back, runs normal scans, index scans, historic reads, change scans, update-history scans, and then reopens the database to confirm persistence.
