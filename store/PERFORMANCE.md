# Maryk 0.14 datastore performance

Compare stores by the operations and deployment your application needs. These
measurements come from a **0.14 development snapshot measured on 15 November
2026**, using Maryk's typed request API. They are not a benchmark of the final
0.14 release or a production capacity guarantee.

## Store configurations

| Store | Measured configuration | Suitable use |
| --- | --- | --- |
| Memory | In-process JVM store; volatile data | Tests, prototypes and application state that fits in memory |
| RocksDB | Embedded JVM store on local SSD | Local persistence without a separate database service |
| FoundationDB | JVM client and local single-process 7.3.81 database | Shared server data and distributed transactions; cluster scaling was not measured |
| Remote | Loopback HTTP to a Memory backend in the same JVM; no TLS or SSH | Access to a Maryk service; actual performance depends on its backend and network |
| IndexedDB | Kotlin/JS in Chrome with real IndexedDB | Offline browser records and synchronization |

Persistence and commit guarantees differ. Remote-to-Memory timings do not
represent persistent storage, and local FoundationDB timings do not establish
multi-machine performance.

## Request latency

Lower is better. Times cover complete requests, including validation, indexes,
history and commit behavior. **1,000 µs = 1 ms.** “—” means not measured.

| Store | Full point read (µs) | Full 32-key batch (µs) | Projected 20-record timeline (µs) | Value-changing update (µs) | One-record insert (µs) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Memory | 10.0 | 26.3 | 54.7 | 21.3 | 13.2 |
| RocksDB | 18.4 | 170 | 162 | 57.4 | 70.9 |
| FoundationDB, local | 512 | 1,635 | 2,089 | 6,431 | 5,834 |
| Remote → Memory, loopback | 319 | 534 | 452 | 414 | 356 |
| IndexedDB, Chrome | 105 | 655 | 1,331 | 1,419 | — |

Memory, RocksDB and FoundationDB use the same regular request protocol. Remote
uses fewer requests and connection cooldowns; IndexedDB uses its browser
protocol. These latencies do not establish sustained or concurrent throughput.

## Batching

Batch latency per value is the complete batch latency divided by 32. The derived
advantage is `32 × point-read latency / batch latency`: an estimate against 32
sequential point reads, not a separately measured sequence or throughput gain.

| Store | Batch latency per requested value (µs) | Derived batching advantage (×) |
| --- | ---: | ---: |
| Memory | 0.82 | 12.2 |
| RocksDB | 5.30 | 3.5 |
| FoundationDB, local | 51.1 | 10.0 |
| Remote → Memory, loopback | 16.7 | 19.1 |
| IndexedDB, Chrome | 20.5 | 5.1 |

Group useful keys into bounded requests where possible; byte, transaction and
memory limits still apply.

## Projection

Values are **full → projected**. Selecting fewer fields reduces allocation in
these JVM batches, but small-record latency does not always improve. Allocation
is per complete batch, excludes native storage, and is not retained memory.

| Store | Point read (µs) | 32-key batch (µs) | JVM allocation per 32-key batch (KiB) |
| --- | ---: | ---: | ---: |
| Memory | 10.0 → 10.7 | 26.3 → 25.0 | 26.5 → 23.2 |
| RocksDB | 18.4 → 18.3 | 170 → 160 | 170.3 → 109.5 |
| FoundationDB, local | 512 → 356 | 1,635 → 1,485 | 252.0 → 155.1 |
| Remote → Memory, loopback | 319 → 336 | 534 → 479 | 343.7 → 232.4 |
| IndexedDB, Chrome | 105 → 105 | 655 → 658 | — |

For larger payloads, a separate IndexedDB fixture with 1 MiB per record measured
**18.4 ms** for a narrow indexed selection of 64 records and **61.2 ms** for a
projected batch of 257 records. These are different workloads from the table.

## Variation between runs

Ranges show the minimum–maximum of three run summaries. They are not individual
request ranges, confidence intervals or p95/p99 latency.

| Store | Full point read range (µs) | Full 32-key batch range (µs) | Update range (µs) |
| --- | ---: | ---: | ---: |
| Memory | 10.0–10.4 | 25.1–27.0 | 17.2–23.2 |
| RocksDB | 18.2–18.6 | 169–179 | 56.2–70.3 |
| FoundationDB, local | 511–514 | 1,629–1,637 | 6,310–6,515 |
| Remote → Memory, loopback | 304–352 | 488–670 | 362–484 |
| IndexedDB, Chrome | 104–105 | 628–727 | 1,415–1,693 |

## History and recovery

A separate history fixture used 100 scalar records with 24 updates each. After
2,000 warm-up calls, requesting the latest 20 deltas plus creation took **20.7 µs
in Memory** and **30.2 µs in RocksDB**.

Backup and restore used 100 records with 64 strings and two revisions each.
Times below cover the entire operation; restore targets were opened beforehand,
and restored values and revisions were checked. These are not startup times or
predictions for larger archives.

| Store | History backup (ms) | History restore (ms) |
| --- | ---: | ---: |
| Memory | 65.8 | 65.7 |
| RocksDB | 101.7 | 97.5 |
| FoundationDB, local | 435.6 | 1,337.3 |
| Remote → Memory, loopback | 164.3 | 157.4 |
| IndexedDB, Chrome | 142.2 | 636.7 |

See [Backup and restore](shared/docs/backup-restore.md) for recovery guarantees,
and [index design](../core/docs/index-design.md) for efficient request patterns.

## Measurement notes

- **Data:** 500 patients and 14,000 clinical records, with history enabled.
  FoundationDB used atomic fixture seeding; other main JVM fixtures used ordinary
  seeding. The measured requests retain each engine's own commit behavior.
- **Summary:** median of retained round mean request times within each run, then
  median across three independent runs. The regular JVM protocol discards rounds
  0–1; Remote and browser protocols discard round 0. Timed blocks include result
  checks and harness overhead. Values are not pooled means or request percentiles.
- **Environment:** Apple M5 MacBook Pro, 10 CPU cores, 24 GiB memory, local SSD,
  macOS 27.2 arm64; OpenJDK 21.0.2/G1 with a 64–512 MiB heap; Chrome 156/Kotlin/JS.
- **Evidence:** the frozen sweep contains 336 accepted JVM runs, 12 browser
  profiles and 798 scenario groups, including component controls. Interrupted or
  contaminated runs were replaced. No new benchmarks were run for this document.
- **Limits:** these results do not establish production throughput, tail latency,
  saturation, distributed scaling, WAN/TLS performance, crash recovery,
  encrypted-field throughput or performance on other platforms. Store timing
  differences do not isolate Maryk overhead from engine cost.
