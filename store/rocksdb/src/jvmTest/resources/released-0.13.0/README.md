# RocksDB 0.13.0 compatibility fixture

These are exact bytes written by the unmodified production engine at release tag
`0.13.0`, commit `1113a4851ab8b2c9bfa86756b6f07aa4a3327dbc`. They were not
constructed by reversing the current codecs.

The isolated release archive ran `ReleasedEngineFixtureTest.kt.txt` as a JVM test
with JDK 21, Kotlin 2.4.20 and its pinned RocksDB multiplatform 10.10.1.4 dependency.
The release writer verified every scalar/packed raw floating-point bit pattern,
NANOS timestamp and empty collection through the released engine before closing.
The original native database contains 11 column families and 419 rows. A read-only
native RocksDB iterator exported every family name, key and value verbatim.

`rows.tsv` records `family<TAB>nameHex` or
`row<TAB>familyHex<TAB>keyHex<TAB>valueHex`. An empty last field is an actual empty
value. Rows are sorted textually only for stable review; rehydration installs their
original bytes using the matching historic comparator. `MARYK_META.yml` is the
original release metadata file. `manifest.tsv` is the release writer's logical
oracle, including actual creation/change versions and raw numeric bits.

The current compatibility test rehydrates these bytes without serializing values,
opens the current engine, verifies values, defaults, both indexes and historical
reads, then repeats after reopening. Ordinary empty collection counts were written
at creation; the fixture does not fabricate authority for ambiguous later legacy
collection tombstones.

To regenerate, create an isolated archive with `git archive 0.13.0`, copy the saved
generator into `store/rocksdb/src/jvmTest/kotlin/maryk/datastore/rocksdb/` with a `.kt`
extension, and run:

```sh
./gradlew --offline --console=plain -q :store:rocksdb:jvmTest --tests maryk.datastore.rocksdb.ReleasedEngineFixtureTest
```

The generator refuses to overwrite its `/tmp/maryk-released-engine-fixture` output.
Export that closed database read-only with the `maryk.VersionedComparator` for the
historic families, and retain its newly generated logical manifest. Clock versions
vary between generations; keys and logical cases are deterministic. Native WAL,
options and host log files are intentionally excluded from this portable fixture.

SHA-256 checksums of this fixture generation:

| File | SHA-256 |
| --- | --- |
| `rows.tsv` | `b94b2fc89684031dd709c4fdf052e8bf265bf1019089f4c4ecfe208288dc0f3a` |
| `MARYK_META.yml` | `4ede794dcecabed3b9bb6b892e5a7bcc8078f2c1ad07a3751e35ea373d08cdb4` |
| `manifest.tsv` | `30922072a76f9f90bd1d1f216d798d74d08e67034a1b742e998ac70d891e2e3d` |
| `ReleasedEngineFixtureTest.kt.txt` | `6f88859ca5d29113271183efd5189b90441bb3979144145b93fe8036d51a26a5` |
