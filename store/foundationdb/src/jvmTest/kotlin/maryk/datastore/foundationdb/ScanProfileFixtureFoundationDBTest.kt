package maryk.datastore.foundationdb

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.orders.ascending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.scan
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.test.models.AnyValueSetIndexModel
import maryk.test.models.Option
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class ScanProfileFixtureFoundationDBTest {
    @Test
    fun profileScanHotPathsFixture() = runTest(timeout = 3.minutes) {
        val iterations = System.getProperty("maryk.profile.iterations")?.toIntOrNull() ?: 25
        val objectCount = System.getProperty("maryk.profile.objectCount")?.toIntOrNull() ?: 64

        val store = FoundationDBDataStore.open(
            fdbClusterFilePath = "./fdb.cluster",
            directoryPath = listOf("maryk", "test", "scan-profile-fixture", Uuid.random().toString()),
            dataModelsById = mapOf(
                1u to TestMarykModel,
                2u to AnyValueSetIndexModel,
            ),
            keepAllVersions = true,
        )

        try {
            val testMarykStatuses = store.execute(
                TestMarykModel.add(*Array(objectCount) { index ->
                    TestMarykModel.create {
                        string with "ha-$index"
                        int with (index % 7 - 1).coerceAtMost(6)
                        uint with index.toUInt()
                        double with (index + 1).toDouble()
                        dateTime with LocalDateTime(2024, 1, (index % 28) + 1, 0, 0)
                        bool with (index % 2 == 0)
                        enum with if (index % 2 == 0) Option.V1 else Option.V2
                    }
                })
            ).statuses.map { assertIs<AddSuccess<TestMarykModel>>(it) }

            val anyValueStatuses = store.execute(
                AnyValueSetIndexModel.add(*Array(objectCount) { index ->
                    AnyValueSetIndexModel.create {
                        name with "set-$index"
                        setValues with setOf("tag-${index % 16}", "tag-${(index + 3) % 16}")
                    }
                })
            ).statuses.map { assertIs<AddSuccess<AnyValueSetIndexModel>>(it) }

            testMarykStatuses
                .filterIndexed { index, _ -> index % 4 == 0 }
                .forEach { status ->
                    assertIs<ChangeSuccess<TestMarykModel>>(
                        store.execute(
                            TestMarykModel.change(
                                status.key.change(
                                    Change(TestMarykModel { int::ref } with 5)
                                )
                            )
                        ).statuses.single()
                    )
                }

            testMarykStatuses
                .filterIndexed { index, _ -> index % 8 == 0 }
                .forEach { status ->
                    assertIs<DeleteSuccess<TestMarykModel>>(
                        store.execute(TestMarykModel.delete(status.key)).statuses.single()
                    )
                }

            repeat(iterations) { iteration ->
                store.execute(
                    AnyValueSetIndexModel.scan(
                        where = Equals(
                            AnyValueSetIndexModel { setValues.refToAny() } with "tag-${iteration % 16}"
                        )
                    )
                )

                store.execute(
                    AnyValueSetIndexModel.scan(
                        order = AnyValueSetIndexModel { setValues.refToAny() }.ascending()
                    )
                )

                store.execute(
                    TestMarykModel.scan(
                        where = Equals(TestMarykModel { int::ref } with 5),
                        toVersion = ULong.MAX_VALUE
                    )
                )
            }

            store.execute(
                TestMarykModel.delete(*testMarykStatuses.map { it.key }.toTypedArray(), hardDelete = true)
            )
            store.execute(
                AnyValueSetIndexModel.delete(*anyValueStatuses.map { it.key }.toTypedArray(), hardDelete = true)
            )
        } finally {
            store.close()
        }
    }
}
