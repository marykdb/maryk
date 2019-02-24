package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DefinitionDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.EmptyValueItems
import maryk.core.values.IsValuesGetter
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.lib.uuid.generateUUID
import maryk.yaml.IsYamlReader

/** A key with a Universally Unique ID */
object UUIDKey : IsFixedBytesPropertyReference<Pair<Long, Long>> {
    override val indexKeyPartType = IndexKeyPartType.UUID
    override val byteSize = 16

    override fun getValue(values: IsValuesGetter) = generateUUID()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Pair(
        initLong(reader),
        initLong(reader)
    )

    override fun isForPropertyReference(propertyReference: IsPropertyReference<*, *, *>) = false

    override fun writeStorageBytes(value: Pair<Long, Long>, writer: (byte: Byte) -> Unit) {
        value.first.writeBytes(writer)
        value.second.writeBytes(writer)
    }

    override fun calculateReferenceStorageByteLength() =
        this.indexKeyPartType.index.calculateVarByteLength()

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.indexKeyPartType.index.writeVarBytes(writer)
    }

    internal object Model : DefinitionDataModel<UUIDKey>(
        properties = object : ObjectPropertyDefinitions<UUIDKey>() {}
    ) {
        override fun invoke(values: SimpleObjectValues<UUIDKey>) = UUIDKey

        override fun readJson(reader: IsJsonLikeReader, context: ContainsDefinitionsContext?) =
            if (reader is IsYamlReader) {
                this.values { EmptyValueItems }
            } else {
                super.readJson(reader, context)
            }
    }
}
