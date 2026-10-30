package maryk.core.properties.definitions.contextual

import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.IsDataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext

private object MetadataShortTarget : RootDataModel<MetadataShortTarget>(
    name = "MetadataTarget", keyDefinition = { MetadataShortTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt32, final = true)
}

private object MetadataLongTarget : RootDataModel<MetadataLongTarget>(
    name = "MetadataTarget", keyDefinition = { MetadataLongTarget.id.ref() },
) {
    val id by number(index = 1u, type = UInt64, final = true)
}

class ContextualModelReferenceStorageMetadataTest {
    private val definition = ContextualModelReferenceDefinition<IsDataModel, DefinitionsConversionContext>(
        contextualResolver = { context, name ->
            @Suppress("UNCHECKED_CAST")
            context?.dataModels?.get(name) as? IsDataModelReference<IsDataModel>
                ?: throw DefNotFoundException("Unknown model $name")
        }
    )

    @Test
    fun onlyPersistedSchemaContextsWriteReferenceWidths() {
        val reference = DataModelReference<IsDataModel>(MetadataShortTarget)
        assertEquals("MetadataTarget", encode(reference, DefinitionsConversionContext()))
        val encoded = encode(reference, StorageDefinitionsConversionContext())
        assertEquals("MetadataTarget(4)", encoded)
        assertEquals(4, decode(encoded, StorageDefinitionsConversionContext()).keyLength)
    }

    @Test
    fun sourceMetadataCannotDiscardADeclaredWidthWhenItsTargetResolves() {
        val context = StorageDefinitionsConversionContext().apply {
            dataModels[MetadataShortTarget.Meta.name] = DataModelReference(MetadataShortTarget)
        }
        assertEquals(4, decode("MetadataTarget(4)", context).keyLength)
        assertEquals(8, decode("MetadataTarget(8)", context).keyLength)

        val ordinary = DefinitionsConversionContext().apply {
            dataModels[MetadataLongTarget.Meta.name] = DataModelReference(MetadataLongTarget)
        }
        assertEquals(8, decode("MetadataTarget(4)", ordinary).keyLength)
    }

    private fun encode(reference: IsDataModelReference<IsDataModel>, context: DefinitionsConversionContext): String {
        val cache = WriteCache()
        val bytes = ByteArray(definition.calculateTransportByteLength(reference, cache, context))
        var offset = 0
        definition.writeTransportBytes(reference, cache, { bytes[offset++] = it }, context)
        return bytes.decodeToString()
    }

    private fun decode(value: String, context: DefinitionsConversionContext): IsDataModelReference<IsDataModel> {
        val bytes = value.encodeToByteArray()
        var offset = 0
        return definition.readTransportBytes(bytes.size, { bytes[offset++] }, context)
    }
}
