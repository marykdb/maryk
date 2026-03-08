@file:Suppress("unused")

package maryk.core.properties.references

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.test.models.MarykTypeEnum
import kotlin.test.Test
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
        val first = CachedRootModelA { info { name { familyName { lastName::ref } } } }
        val second = CachedRootModelA { info { name { familyName { lastName::ref } } } }

        assertSame(first, second)
    }

    @Test
    fun doesNotReuseReferenceAcrossDifferentEmbeddedParentChains() {
        val first = CachedRootModelA { info { name { familyName { lastName::ref } } } }
        val second = CachedRootModelB { info { name { familyName { lastName::ref } } } }

        assertNotSame(first, second)
        assertNotSame(first.parentReference, second.parentReference)
    }

    @Test
    fun reusesTypedAndSimpleTypedReferencesForSameParent() {
        val typedFirst = CachedMultiTypeModel { multi refAtType MarykTypeEnum.T3 }
        val typedSecond = CachedMultiTypeModel { multi refAtType MarykTypeEnum.T3 }
        val simpleFirst = CachedMultiTypeModel { multi refAtType MarykTypeEnum.T1 }
        val simpleSecond = CachedMultiTypeModel { multi refAtType MarykTypeEnum.T1 }

        assertSame(typedFirst, typedSecond)
        assertSame(simpleFirst, simpleSecond)
    }
}
