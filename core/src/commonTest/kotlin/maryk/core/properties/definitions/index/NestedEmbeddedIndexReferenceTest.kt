package maryk.core.properties.definitions.index

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private object FamilyNameModel : DataModel<FamilyNameModel>() {
    val lastName by string(index = 1u, required = false)
    val prefix by string(index = 2u, required = false)
}

private object NameModel : DataModel<NameModel>() {
    val familyName by embed(index = 1u, required = false, dataModel = { FamilyNameModel })
    val firstNames by string(index = 2u, required = false)
}

private object InfoModel : DataModel<InfoModel>() {
    val name by embed(index = 1u, required = false, dataModel = { NameModel })
    val birthDate by string(index = 2u, required = false)
}

private object NestedIndexModel : RootDataModel<NestedIndexModel>(
    indexes = {
        listOf(
            NestedIndexModel { info { name { firstNames::ref } } },
            NestedIndexModel { info { birthDate::ref } },
            NestedIndexModel { info { name { familyName { lastName::ref } } } },
            NestedIndexModel { info { name { familyName { prefix::ref } } } },
            NestedIndexModel { info { InfoModel.name { NameModel.familyName { FamilyNameModel.lastName::ref } } } },
            NestedIndexModel { info { InfoModel.name { NameModel.familyName { FamilyNameModel.prefix::ref } } } },
        )
    },
    minimumKeyScanByteRange = 0u,
) {
    val info by embed(index = 1u, required = false, dataModel = { InfoModel })
}

class NestedEmbeddedIndexReferenceTest {
    private val values = NestedIndexModel.create {
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

    @Test
    fun resolvesDeepEmbeddedReferences() {
        assertEquals("Mila Anne", values[NestedIndexModel { info { name { firstNames::ref } } }])
        assertEquals("1980-01-01", values[NestedIndexModel { info { birthDate::ref } }])
        assertEquals("Loon", values[NestedIndexModel { info { name { familyName { lastName::ref } } } }])
        assertEquals("van", values[NestedIndexModel { info { name { familyName { prefix::ref } } } }])
    }

    @Test
    fun createsIndexBytesForDeepEmbeddedReferences() {
        val rootKey = byteArrayOf(0x01, 0x02, 0x03)

        val firstNamesIndex = NestedIndexModel.Meta.indexes!![0]
        val birthDateIndex = NestedIndexModel.Meta.indexes!![1]
        val familyNameIndex = NestedIndexModel.Meta.indexes!![2]
        val prefixIndex = NestedIndexModel.Meta.indexes!![3]
        val explicitFamilyNameIndex = NestedIndexModel.Meta.indexes!![4]
        val explicitPrefixIndex = NestedIndexModel.Meta.indexes!![5]

        assertContentEquals(
            firstNamesIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel { info { name { firstNames::ref } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            birthDateIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel { info { birthDate::ref } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            familyNameIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel { info { name { familyName { lastName::ref } } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            prefixIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel { info { name { familyName { prefix::ref } } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            explicitFamilyNameIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel {
                info {
                    InfoModel.name {
                        NameModel.familyName {
                            FamilyNameModel.lastName::ref
                        }
                    }
                }
            }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            explicitPrefixIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel {
                info {
                    InfoModel.name {
                        NameModel.familyName {
                            FamilyNameModel.prefix::ref
                        }
                    }
                }
            }.toStorageByteArraysForIndex(values, rootKey).single()
        )
    }
}
