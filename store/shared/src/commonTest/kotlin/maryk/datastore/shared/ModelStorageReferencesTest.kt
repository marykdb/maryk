package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.types.Bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private object ReferenceLeaf : DataModel<ReferenceLeaf>() {
    val secret by string(1u, sensitive = true, unique = true)
    val alias by string(2u, unique = true)
    val child by embed(3u, required = false, dataModel = { ReferenceLeaf })
}

private object ReferenceRoot : RootDataModel<ReferenceRoot>() {
    val left by embed(1u, dataModel = { ReferenceLeaf })
    val right by embed(2u, dataModel = { ReferenceLeaf })
}

private object IndexedSecret : RootDataModel<IndexedSecret>(indexes = { listOf(IndexedSecret.secret.ref()) }) {
    val secret by string(1u, sensitive = true)
}

private object NonUniqueCollections : RootDataModel<NonUniqueCollections>() {
    val name by string(1u)
    val entries by list(2u, valueDefinition = StringDefinition())
    val labels by map(3u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition())
}

private object UniqueListMembers : RootDataModel<UniqueListMembers>() {
    val entries by list(1u, valueDefinition = StringDefinition(unique = true))
}

private object UniqueMapMembers : RootDataModel<UniqueMapMembers>() {
    val entries by map(1u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(unique = true))
}

class ModelStorageReferencesTest {
    @Test
    fun uniquenessPreflightRetainsNestedAndMemberChecks() {
        assertFalse(NonUniqueCollections.mayContainUniqueValues())
        assertFalse(IndexedSecret.mayContainUniqueValues())
        assertTrue(ReferenceRoot.mayContainUniqueValues())
        assertTrue(UniqueListMembers.mayContainUniqueValues())
        assertTrue(UniqueMapMembers.mayContainUniqueValues())
    }

    @Test
    fun reusedEmbeddedSchemasHaveIndependentPrefixesAndCyclesTerminate() {
        val expected = listOf(ReferenceRoot.ref { left { secret } }, ReferenceRoot.ref { right { secret } })
            .map { Bytes(it.toStorageByteArray()) }
        val sensitive = collectSensitiveReferences(1u, ReferenceRoot)
        assertEquals(expected, sensitive.sensitiveReferences.map(::Bytes))
        assertEquals(expected, sensitive.sensitiveUniqueReferences.map(::Bytes))
        val unique = collectUniqueReferences(ReferenceRoot).map(::Bytes)
        assertEquals(4, unique.size)
        assertTrue(unique.containsAll(expected))
        assertTrue(Bytes(ReferenceRoot.ref { left { alias } }.toStorageByteArray()) in unique)
        assertTrue(Bytes(ReferenceRoot.ref { right { alias } }.toStorageByteArray()) in unique)
    }

    @Test
    fun sensitiveIndexesAreRejectedWithModelContext() {
        val error = assertFailsWith<RequestException> { collectSensitiveReferences(7u, IndexedSecret) }
        assertTrue(error.message.orEmpty().contains("modelId=7"))
        assertTrue(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun storedUniqueDescriptorsAreMergedByContentInDeclaredOrder() {
        val declared = listOf(byteArrayOf(1), byteArrayOf(2))
        val merged = mergeUniqueReferences(declared, listOf(byteArrayOf(2), byteArrayOf(3), byteArrayOf(3)))
        assertEquals(listOf(Bytes(byteArrayOf(1)), Bytes(byteArrayOf(2)), Bytes(byteArrayOf(3))), merged.map(::Bytes))
    }
}
