# Maryk App Desktop Guide

## Overview

Maryk App is a Compose Multiplatform desktop UI for browsing and editing Maryk stores.
Runs locally, reads model definitions from the store, and talks directly to RocksDB or FoundationDB.

### What it provides

- Store connection manager with saved entries and multiple browser windows.
- Model catalog with counts and schema export.
- Data browser with scan, sort, filters, and pinned columns.
- Record inspector with YAML, history, and diffs.
- Form-based editor with validation.
- Import and export for models and data.

## UI layout

- Stores window: list of saved stores, add/edit/remove, double click to open.
- Browser window: top bar with store info, left catalog, center results, right inspector.
- Panels resizable. Catalog and inspector toggles in the top bar and shortcuts.

## Stores

- RocksDB: directory path to the database.
- FoundationDB: directory path (slash separated), optional cluster file, optional tenant.
- Remote: HTTP URL to a served store, optional SSH tunnel.
- Saved in `~/.maryk/app/stores.conf`.
- Notes:
  - RocksDB cannot be opened by two processes at once.
  - FoundationDB requires `libfdb_c` (see `store/foundationdb`).

## Remote stores (HTTP/SSH)

Maryk App can connect to a Remote store served over HTTP, with optional SSH tunneling.

Flow:
1) Start a server on the remote host (`maryk --exec "serve ..."`) to expose the store over HTTP.
2) In the app, add a store with type `Remote`.
3) Set `Remote URL` (example: `http://remote-host:8210`).
4) Optional SSH: enable `Use SSH tunnel` and set:
   - `SSH host` (the remote machine).
   - `SSH user` (optional).
   - `SSH port` (optional, default 22).
   - `Local port` (optional; auto-select if empty).
   - `Identity file` (optional).

Notes:
- Only plain HTTP is supported; use SSH for encryption.
- If the server binds to `127.0.0.1` on the remote host, set the URL to `http://127.0.0.1:8210` and enable SSH with the remote host as the tunnel target.

## Catalog (models)

- Search by name with fuzzy match.
- Shows model id, version badge, and row count (capped at 100+).
- Context menu: export model schema, export model data.

## Results: Data tab

- Scan results for selected model, paged loading.
- Sort by key or by index; toggle ascending or descending.
- Filter button opens builder or raw YAML editor.
- Add record button opens the form editor.
- Row context menu: edit, copy key, copy data as JSON/YAML, export row, delete.
- Selection supports multi-select and keyboard navigation.
- Pinned columns: pin fields from Model tab to add columns in the grid.

## Results: Model tab

- Model tree with embedded fields and multi-type branches.
- Key and index chips; click to select field.
- Pin supported fields to show in the data grid.
- Details panel: constraints, defaults, and type info.
- Raw panel: quick definition summary and enum cases.

## Inspector

Tabs depend on mode:
- Data: tree view of record fields, search, reference links with hover preview, edit button.
- Raw: YAML view with search, copy, and raw editor.
- History: timeline of versions and diff viewer.

## Editing records

- Form editor for add and edit.
- Supports lists, sets, maps, embedded objects/values, enums, references, date/time pickers.
- Validation on save with inline errors.
- Raw YAML edit applies with a version guard to avoid overwrites.

## Filtering and search

- Builder supports nested groups (and/or) and negation.
- Operators: equals, comparisons, range, prefix, regex, exists, value in.
- Raw tab accepts Maryk YAML filter expressions.

## Import and export

- Model schema export: JSON, YAML, Proto, Kotlin.
- Data export: row, model, or all data; JSON, YAML, Proto; optional version history.
- Import: format and scope auto-detected from file contents.
- Model name guessed from file name; fallback prompts model picker.
- Import expects Maryk export formats (ValuesWithMetaData and versioned changes).

## History and references

- History uses `getChanges`; requires a store that keeps versions.
- Diff compares two versions and shows changes in YAML.
- Reference values link to target record; back bar returns to previous record.

## Shortcuts

- Cmd/Ctrl+R reload scan
- Cmd/Ctrl+B toggle catalog
- Cmd/Ctrl+I toggle inspector
- Esc closes open drawer
- Arrow keys / Page Up / Page Down move selection; Enter or Space opens record
- Cmd/Ctrl+click multi-select; Shift range select
- Menu: Data -> Import data, Export all models, Export data

## Files and preferences

- Store list: `~/.maryk/app/stores.conf`
- UI preferences (pins, panel state) stored in Java Preferences node `io.maryk.app.ui`.
