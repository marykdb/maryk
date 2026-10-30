package maryk.datastore.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.reference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext

private object SourceShortTarget : RootDataModel<SourceShortTarget>(
    name = "SourceTarget",
    keyDefinition = { SourceShortTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object SourceLongTarget : RootDataModel<SourceLongTarget>(
    name = "SourceTarget",
    keyDefinition = { SourceLongTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt64, final = true)
}

private object SourceReferenceKeyOwner : RootDataModel<SourceReferenceKeyOwner>(
    name = "BeforeSourceTarget", keyDefinition = { SourceReferenceKeyOwner.target.ref() },
) {
    val target by reference(index = 1u, dataModel = { SourceShortTarget }, final = true)
}

class StoredModelDefinitionsContextTest {
    @Test
    fun forwardRootReferencesUseSourceWidthAfterTheCatalogueIsLoaded() {
        val catalogue = StoredModelDefinitionsContext()
        val source = readSourceReference(catalogue.newSourcePairContext())
        catalogue.registerSourceRoot(SourceShortTarget)

        assertEquals(4, source.byteSize)
        assertFalse(ReferenceDefinition(dataModel = { SourceLongTarget }).compatibleWith(source))

        // Publication of the new target must not replace the opening source baseline.
        catalogue.registerSourceRoot(SourceLongTarget)
        assertEquals(4, source.byteSize)
    }

    @Test
    fun pairLocalRecursiveDefinitionsOverrideOtherRootsWithTheSameName() {
        val catalogue = StoredModelDefinitionsContext()
        catalogue.registerSourceRoot(SourceLongTarget)
        val pair = catalogue.newSourcePairContext()
        val source = readSourceReference(pair)
        pair.dataModels[SourceShortTarget.Meta.name] = DataModelReference(SourceShortTarget)

        assertEquals(4, source.byteSize)
        assertEquals(8, readSourceReference(catalogue.newSourcePairContext()).byteSize)
    }

    @Test
    fun missingSourceTargetsNeverFallBackToConfiguredModels() {
        val catalogue = StoredModelDefinitionsContext()
        catalogue.dataModels[SourceLongTarget.Meta.name] = DataModelReference(SourceLongTarget)
        val source = readSourceReference(catalogue.newSourcePairContext())

        assertFailsWith<DefNotFoundException> { source.byteSize }
    }

    @Test
    fun legacyReferenceKeysLoadAfterTheirSourceTargetsWithoutCurrentSchemaFallback() {
        val catalogue = StoredModelDefinitionsContext()
        var ownerReads = 0
        val loaded = loadStoredModelDefinitions(listOf(1u, 2u)) { id ->
            if (id == 1u) ownerReads++
            val original = if (id == 1u) SourceReferenceKeyOwner else SourceShortTarget
            val writerContext = DefinitionsConversionContext()
            val cache = WriteCache()
            val length = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(original, cache, writerContext)
            val bytes = ByteArray(length)
            var offset = 0
            RootDataModel.Model.Serializer.writeObjectProtoBuf(original, cache, { bytes[offset++] = it }, writerContext)
            offset = 0
            RootDataModel.Model.Serializer.readProtoBuf(length, { bytes[offset++] }, catalogue.newSourcePairContext())
                .toDataObject().also(catalogue::registerSourceRoot)
        }

        assertEquals(2, ownerReads)
        assertEquals(4, loaded.getValue(1u).Meta.keyByteSize)
    }

    @Test
    fun explicitParentWidthsSurviveASeparateTargetMigration() {
        val catalogue = StoredModelDefinitionsContext()
        val source = readSourceReference(catalogue.newSourcePairContext(), StorageDefinitionsConversionContext())
        assertEquals(4, source.byteSize)
        catalogue.registerSourceRoot(SourceLongTarget)
        assertEquals(4, source.byteSize)
        assertFalse(ReferenceDefinition(dataModel = { SourceLongTarget }).compatibleWith(source))
    }

    @Test
    fun referenceValueDecodingUsesDeclaredSourceWidthWithoutResolvingNewerTargets() {
        val catalogue = StoredModelDefinitionsContext()
        val source = readSourceReference(catalogue.newSourcePairContext(), StorageDefinitionsConversionContext())
        for (targetRegistered in listOf(false, true)) {
            if (targetRegistered) catalogue.registerSourceRoot(SourceLongTarget)
            var reads = 0
            val key = source.readStorageBytes(4) { (++reads).toByte() }
            assertEquals(4, reads)
            assertEquals(4, key.size)
            assertEquals(key, source.fromNativeType(byteArrayOf(1, 2, 3, 4)))
            assertEquals(key, source.fromString(key.toString()))
        }
    }

    @Test
    fun unresolvedSourceCataloguesStopWithoutRetryingForever() {
        var reads = 0
        assertFailsWith<DefNotFoundException> {
            loadStoredModelDefinitions(listOf(1u)) {
                reads++
                throw DefNotFoundException("Missing source target")
            }
        }
        assertEquals(1, reads)
    }

    private fun readSourceReference(
        context: DefinitionsConversionContext,
        writerContext: DefinitionsConversionContext = DefinitionsConversionContext(),
    ): ReferenceDefinition<*> {
        val source: ReferenceDefinition<*> = ReferenceDefinition(dataModel = { SourceShortTarget })
        val cache = WriteCache()
        val length = ReferenceDefinition.Model.Serializer.calculateObjectProtoBufLength(source, cache, writerContext)
        val bytes = ByteArray(length)
        var offset = 0
        ReferenceDefinition.Model.Serializer.writeObjectProtoBuf(source, cache, { bytes[offset++] = it }, writerContext)
        offset = 0
        return ReferenceDefinition.Model.Serializer.readProtoBuf(length, { bytes[offset++] }, context).toDataObject()
    }
}
