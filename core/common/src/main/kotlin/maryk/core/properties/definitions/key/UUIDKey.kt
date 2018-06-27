package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.lib.uuid.generateUUID
import maryk.yaml.IsYamlReader

/** A key with a Universally Unique ID */
object UUIDKey: FixedBytesProperty<Pair<Long, Long>>() {
    override val keyPartType = KeyPartType.UUID
    override val byteSize = 16

    override fun <T : Any> getValue(dataModel: IsDataModel<T>, dataObject: T) = generateUUID()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Pair(
        initLong(reader),
        initLong(reader)
    )

    override fun writeStorageBytes(value: Pair<Long, Long>, writer: (byte: Byte) -> Unit) {
        value.first.writeBytes(writer)
        value.second.writeBytes(writer)
    }

    internal object Model : DefinitionDataModel<UUIDKey>(
        properties = object : PropertyDefinitions<UUIDKey>() {}
    ) {
        override fun invoke(map: Map<Int, *>) = UUIDKey

        override fun readJson(reader: IsJsonLikeReader, context: DataModelContext?): DataObjectMap<UUIDKey> {
            return if (reader is IsYamlReader) {
                DataObjectMap(this, mapOf())
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
