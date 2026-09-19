@file:Suppress("unused")

package maryk.core.properties.references

import kotlinx.datetime.LocalTime
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.test.models.MarykTypeEnum
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private object CachedSharedLastNameModel : DataModel<CachedSharedLastNameModel>() {
    val lastName by string(index = 1u, required = false)
    val prefix by string(index = 2u, required = false)
}

private object CachedSharedNameModel : DataModel<CachedSharedNameModel>() {
    val familyName by embed(index = 1u, required = false, dataModel = { CachedSharedLastNameModel })
}

private object CachedInfoModelA : DataModel<CachedInfoModelA>() {
    val name by embed(index = 1u, required = false, dataModel = { CachedSharedNameModel })
}

private object CachedInfoModelB : DataModel<CachedInfoModelB>() {
    val title by string(index = 1u, required = false)
    val name by embed(index = 2u, required = false, dataModel = { CachedSharedNameModel })
}

private object CachedRootModelA : RootDataModel<CachedRootModelA>() {
    val info by embed(index = 1u, required = false, dataModel = { CachedInfoModelA })
}

private object CachedRootModelB : RootDataModel<CachedRootModelB>() {
    val info by embed(index = 1u, required = false, dataModel = { CachedInfoModelB })
}

private object CachedMultiTypeModel : RootDataModel<CachedMultiTypeModel>() {
    val multi by multiType(
        index = 1u,
        required = false,
        typeEnum = MarykTypeEnum,
        typeIsFinal = false
    )
}

class ReferenceCacheTest {
    @Test
    fun reusesReferenceForSameEmbeddedParentChain() {
        val first = CachedRootModelA.ref { info { name { familyName { lastName } } } }
        val second = CachedRootModelA.ref { info { name { familyName { lastName } } } }

        assertSame(first, second)
    }

    @Test
    fun doesNotReuseReferenceAcrossDifferentEmbeddedParentChains() {
        val first = CachedRootModelA.ref { info { name { familyName { lastName } } } }
        val second = CachedRootModelB.ref { info { name { familyName { lastName } } } }

        assertNotSame(first, second)
        assertNotSame(first.parentReference, second.parentReference)
    }

    @Test
    fun reusesTypedAndSimpleTypedReferencesForSameParent() {
        val typedFirst = CachedMultiTypeModel.ref { multi atType MarykTypeEnum.T3 }
        val typedSecond = CachedMultiTypeModel.ref { multi atType MarykTypeEnum.T3 }
        val simpleFirst = CachedMultiTypeModel.ref { multi atType MarykTypeEnum.T1 }
        val simpleSecond = CachedMultiTypeModel.ref { multi atType MarykTypeEnum.T1 }

        assertSame(typedFirst, typedSecond)
        assertSame(simpleFirst, simpleSecond)
    }

    @Test
    fun doesNotRetainDynamicallySelectedCollectionReferences() {
        TestMarykModel.ref { list }
        TestMarykModel.ref { map }
        val listCacheSize = TestMarykModel.list.refCache.value?.size ?: 0
        val mapCacheSize = TestMarykModel.map.refCache.value?.size ?: 0

        repeat(60) {
            TestMarykModel.ref { list at it.toUInt() }
            TestMarykModel.ref { map at LocalTime(1, 0, it) }
        }

        assertEquals(listCacheSize, TestMarykModel.list.refCache.value?.size ?: 0)
        assertEquals(mapCacheSize, TestMarykModel.map.refCache.value?.size ?: 0)
    }
}
