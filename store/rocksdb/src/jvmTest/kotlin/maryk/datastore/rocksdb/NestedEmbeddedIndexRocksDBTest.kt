package maryk.datastore.rocksdb

import kotlinx.coroutines.test.runTest
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.string
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlin.test.Test
import kotlin.test.assertIs

private object FamilyNameEmbeddedModel : DataModel<FamilyNameEmbeddedModel>() {
    val lastName by string(index = 1u, required = false)
    val prefix by string(index = 2u, required = false)
}

private object NameEmbeddedModel : DataModel<NameEmbeddedModel>() {
    val familyName by embed(index = 1u, required = false, dataModel = { FamilyNameEmbeddedModel })
    val firstNames by string(index = 2u, required = false)
}

private object InfoEmbeddedModel : DataModel<InfoEmbeddedModel>() {
    val name by embed(index = 1u, required = false, dataModel = { NameEmbeddedModel })
    val birthDate by string(index = 2u, required = false)
}

private object NestedEmbeddedIndexRootModel : RootDataModel<NestedEmbeddedIndexRootModel>(
    indexes = {
        listOf(
            NestedEmbeddedIndexRootModel { info { name { firstNames::ref } } },
            NestedEmbeddedIndexRootModel { info { birthDate::ref } },
            NestedEmbeddedIndexRootModel { info { name { familyName { lastName::ref } } } },
            NestedEmbeddedIndexRootModel { info { name { familyName { prefix::ref } } } },
            Multiple(
                NestedEmbeddedIndexRootModel { info { name { familyName { lastName::ref } } } },
                NestedEmbeddedIndexRootModel { info { name { firstNames::ref } } },
            )
        )
    },
    minimumKeyScanByteRange = 0u,
) {
    val info by embed(index = 1u, required = false, dataModel = { InfoEmbeddedModel })
}

class NestedEmbeddedIndexRocksDBTest {
    @Test
    fun addWithDeepEmbeddedIndexes() = runTest {
        val folder = createTestDBFolder("nested-embedded-index")

        try {
            val store = RocksDBDataStore.open(
                relativePath = folder,
                keepAllVersions = true,
                dataModelsById = mapOf(1u to NestedEmbeddedIndexRootModel),
            )

            val values = NestedEmbeddedIndexRootModel.create {
                info with {
                    name with {
                        firstNames with "Mila Anne"
                        familyName with {
                            lastName with "Loon"
                            prefix with "van"
                        }
                    }
                    birthDate with "1980-01-01"
                }
            }

            val response = store.execute(
                NestedEmbeddedIndexRootModel.add(values)
            )

            assertIs<AddSuccess<*>>(response.statuses.single())

            store.close()
        } finally {
            deleteFolder(folder)
        }
    }
}
