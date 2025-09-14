---
title: Why choose Maryk?
description: Friendly reasons to pick Maryk — less drift, smaller payloads, and safer evolution.
---

Choosing tools is about trade‑offs. Maryk is a great fit when you want a **single, trustworthy model** and **efficient data flows** across platforms.

### Top reasons to pick Maryk

1) **One model, everywhere**  
Define the schema once in Kotlin. Reuse it on mobile, desktop, server, and web. Fewer contract mismatches, better autocomplete, fewer “why is this field missing?” moments.

2) **Version‑aware by design**  
Ask for “only changes since X” or “what did this look like at time T?”. Stream live updates (add/change/remove) without bolting on a second system.

3) **Smaller, focused payloads**  
Fetch only the fields you need with reference graphs (field selection). Compose filters and aggregations to move less data over the wire.

4) **Storage freedom without rewrites**  
Start with the in‑memory store for tests. Embed RocksDB for apps and single‑node servers. Move to FoundationDB for ACID and scale. Same query API across engines via a shared store layer.

5) **Safer evolution**  
Serialize schemas and run compatibility checks across clients. Built‑in migration hooks when persistent stores change.

### When not to use Maryk

- You need ad‑hoc SQL joins across many unrelated tables at query time.
- You’re doing heavy OLAP or full‑text search (pair Maryk with analytics/search tools).

### If that sounds good

- Read [Data Design](/data-modeling/data-design/) to understand the building blocks.
- Choose an engine in [Stores](/stores/) to match your deployment.
