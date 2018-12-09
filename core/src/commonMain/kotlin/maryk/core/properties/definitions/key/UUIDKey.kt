package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.core.values.ValueItems
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.lib.uuid.generateUUID
import maryk.yaml.IsYamlReader

/** A key with a Universally Unique ID */
object UUIDKey: FixedBytesProperty<Pair<Long, Long>> {
    override val keyPartType = KeyPartType.UUID
    override val byteSize = 16

    override fun <DO: Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsObjectDataModel<DO, P>, dataObject: DO) = generateUUID()

    override fun <DM : IsValuesDataModel<*>> getValue(dataModel: DM, values: Values<DM, *>) = generateUUID()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Pair(
        initLong(reader),
        initLong(reader)
    )

    override fun isForPropertyReference(propertyReference: IsPropertyReference<*, *, *>) = false

    override fun writeStorageBytes(value: Pair<Long, Long>, writer: (byte: Byte) -> Unit) {
        value.first.writeBytes(writer)
        value.second.writeBytes(writer)
    }

    internal object Model : DefinitionDataModel<UUIDKey>(
        properties = object : ObjectPropertyDefinitions<UUIDKey>() {}
    ) {
        override fun invoke(values: SimpleObjectValues<UUIDKey>) = UUIDKey

        override fun readJson(reader: IsJsonLikeReader, context: ContainsDefinitionsContext?): SimpleObjectValues<UUIDKey> {
            return if (reader is IsYamlReader) {
                this.values { ValueItems() }
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
