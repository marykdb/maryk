# Maryk App Desktop Guide

## Overview

The app is designed as a database browser with three main panes:

1. **Stores** (left sidebar)
   - Manage saved connections (add/edit/remove).
   - Connect and disconnect.

2. **Browser** (center + right panes)
   - **Models** list with search.
   - **Records** scan view with filter + field selection.
   - **Record details** with metadata and YAML editor.

## Connecting

- **RocksDB**: provide the data directory path.
- **FoundationDB**: provide the directory path (slash-separated), optional cluster file, and tenant.

## Scan options

- **Filter** uses Maryk YAML filter expressions.
- **Show fields** accepts comma-separated property reference paths (e.g. `info.name.firstName,info.age`).
- **Limit** controls page size.
- **Include deleted** toggles soft-deleted records.

## Editing

- Open a record to see metadata and YAML.
- Edit YAML and press **Apply** to update.
- Use **Delete** to soft-delete or **Hard delete** to remove permanently.
- If a record is soft-deleted, **Restore** is available.

## Storage

Saved store definitions are stored in `~/.maryk/app/stores.conf` on desktop.
