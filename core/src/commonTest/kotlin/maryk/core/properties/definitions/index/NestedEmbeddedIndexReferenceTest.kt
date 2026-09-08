package maryk.core.properties.definitions.index

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
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
            NestedIndexModel.ref { info { name { firstNames } } },
            NestedIndexModel.ref { info { birthDate } },
            NestedIndexModel.ref { info { name { familyName { lastName } } } },
            NestedIndexModel.ref { info { name { familyName { prefix } } } },
            NestedIndexModel.ref { info { InfoModel.name { NameModel.familyName { FamilyNameModel.lastName } } } },
            NestedIndexModel.ref { info { InfoModel.name { NameModel.familyName { FamilyNameModel.prefix } } } },
        )
    },
    minimumKeyScanByteRange = 0u,
) {
    val info by embed(index = 1u, required = false, dataModel = { InfoModel })
}

private object SharedLastNameModel : DataModel<SharedLastNameModel>() {
    val lastName by string(index = 1u, required = false)
}

private object SharedNameModel : DataModel<SharedNameModel>() {
    val familyName by embed(index = 1u, required = false, dataModel = { SharedLastNameModel })
}

private object CacheCollisionInfoModelA : DataModel<CacheCollisionInfoModelA>() {
    val name by embed(index = 1u, required = false, dataModel = { SharedNameModel })
}

private object CacheCollisionInfoModelB : DataModel<CacheCollisionInfoModelB>() {
    val marker by number(index = 1u, type = UInt32, required = false)
    val name by embed(index = 2u, required = false, dataModel = { SharedNameModel })
}

private object CacheCollisionRootModelA : RootDataModel<CacheCollisionRootModelA>(
    indexes = {
        listOf(
            CacheCollisionRootModelA.ref { info { name { familyName { lastName } } } }
        )
    },
    minimumKeyScanByteRange = 0u,
) {
    val info by embed(index = 1u, required = false, dataModel = { CacheCollisionInfoModelA })
}

private object CacheCollisionRootModelB : RootDataModel<CacheCollisionRootModelB>(
    indexes = {
        listOf(
            CacheCollisionRootModelB.ref { info { name { familyName { lastName } } } }
        )
    },
    minimumKeyScanByteRange = 0u,
) {
    val info by embed(index = 1u, required = false, dataModel = { CacheCollisionInfoModelB })
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
        assertEquals("Mila Anne", values[NestedIndexModel.ref { info { name { firstNames } } }])
        assertEquals("1980-01-01", values[NestedIndexModel.ref { info { birthDate } }])
        assertEquals("Loon", values[NestedIndexModel.ref { info { name { familyName { lastName } } } }])
        assertEquals("van", values[NestedIndexModel.ref { info { name { familyName { prefix } } } }])
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
            NestedIndexModel.ref { info { name { firstNames } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            birthDateIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel.ref { info { birthDate } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            familyNameIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel.ref { info { name { familyName { lastName } } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            prefixIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel.ref { info { name { familyName { prefix } } } }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            explicitFamilyNameIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel.ref {
                info {
                    InfoModel.name {
                        NameModel.familyName {
                            FamilyNameModel.lastName
                        }
                    }
                }
            }.toStorageByteArraysForIndex(values, rootKey).single()
        )
        assertContentEquals(
            explicitPrefixIndex.toStorageByteArraysForIndex(values, rootKey).single(),
            NestedIndexModel.ref {
                info {
                    InfoModel.name {
                        NameModel.familyName {
                            FamilyNameModel.prefix
                        }
                    }
                }
            }.toStorageByteArraysForIndex(values, rootKey).single()
        )
    }

    @Test
    fun resolvesSharedEmbeddedReferencesAcrossDifferentParentLayouts() {
        CacheCollisionRootModelA.ref { info { name { familyName { lastName } } } }

        val values = CacheCollisionRootModelB.create {
            info with {
                marker with 7u
                name with {
                    familyName with {
                        lastName with "Different Parent Layout"
                    }
                }
            }
        }

        assertEquals(
            "Different Parent Layout",
            values[CacheCollisionRootModelB.ref { info { name { familyName { lastName } } } }]
        )
    }
}
