package maryk.core.models.migration

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.set
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.MigrationDefinitionsConversionContext
import maryk.core.query.StorageDefinitionsConversionContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

private class CanonicalSchema(reverse: Boolean, ordered: List<String> = listOf("first", "second")) : RootDataModel<CanonicalSchema>(name = "CanonicalSchema") {
    private val pairs = listOf("first" to "one", "second" to "two").let { if (reverse) it.reversed() else it }
    val mapping by map(1u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(
        keyDefinition = StringDefinition(), valueDefinition = StringDefinition(),
    ), default = pairs.map { it.first to pairs.toMap() }.toMap())
    val labels by set(2u, valueDefinition = StringDefinition(), default = pairs.map { it.first }.toSet())
    val groups by map(3u, keyDefinition = StringDefinition(), valueDefinition = SetDefinition(valueDefinition = StringDefinition()),
        default = pairs.map { it.first to pairs.map { pair -> pair.second }.toSet() }.toMap())
    val sequence by list(4u, valueDefinition = StringDefinition(), default = ordered)
}

class MigrationSchemaEncodingTest {
    @Test
    fun onlyFingerprintEncodingCanonicalizesUnorderedDefaults() {
        assertFalse(encode(CanonicalSchema(false), StorageDefinitionsConversionContext())
            .contentEquals(encode(CanonicalSchema(true), StorageDefinitionsConversionContext())))
        assertContentEquals(encode(CanonicalSchema(false), MigrationDefinitionsConversionContext()),
            encode(CanonicalSchema(true), MigrationDefinitionsConversionContext()))
        assertFalse(encode(CanonicalSchema(false), MigrationDefinitionsConversionContext()).contentEquals(
            encode(CanonicalSchema(false, listOf("second", "first")), MigrationDefinitionsConversionContext())))
    }

    private fun encode(model: RootDataModel<*>, context: DefinitionsConversionContext): ByteArray {
        val cache = WriteCache()
        val length = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(model, cache, context)
        val bytes = ByteArray(length)
        var offset = 0
        RootDataModel.Model.Serializer.writeObjectProtoBuf(model, cache, { bytes[offset++] = it }, context)
        return bytes
    }
}
