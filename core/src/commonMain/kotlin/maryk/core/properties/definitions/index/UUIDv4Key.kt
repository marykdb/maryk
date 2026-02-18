@file:OptIn(ExperimentalUuidApi::class)

package maryk.core.properties.definitions.index

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.DefinitionModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.EmptyValueItems
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.yaml.IsYamlReader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A key with a Universally Unique ID v4 */
object UUIDv4Key : IsFixedBytesPropertyReference<Uuid> {
    override val indexKeyPartType = IndexKeyPartType.UUIDv4
    override val byteSize = 16
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override fun getValue(values: IsValuesGetter) = Uuid.generateV4()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Uuid.fromLongs(
        initLong(reader),
        initLong(reader)
    )

    override fun isForPropertyReference(propertyReference: IsPropertyReference<*, *, *>) = false
    override fun toQualifierStorageByteArray() = null

    override fun writeStorageBytes(value: Uuid, writer: (byte: Byte) -> Unit) {
        value.toLongs { first, second ->
            first.writeBytes(writer)
            second.writeBytes(writer)
        }
    }

    override fun calculateReferenceStorageByteLength() =
        this.indexKeyPartType.index.calculateVarByteLength()

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.indexKeyPartType.index.writeVarBytes(writer)
    }

    override fun toString() = "UUIDv4Key"

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) = true

    internal object Model : DefinitionModel<UUIDv4Key>() {
        override fun invoke(values: SimpleObjectValues<UUIDv4Key>) = UUIDv4Key

        override val Serializer = object: ObjectDataModelSerializer<UUIDv4Key, IsObjectDataModel<UUIDv4Key>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun readJson(reader: IsJsonLikeReader, context: ContainsDefinitionsContext?) =
                if (reader is IsYamlReader) {
                    @Suppress("UNCHECKED_CAST")
                    ObjectValues(this@Model, EmptyValueItems) as ObjectValues<UUIDv4Key, IsObjectDataModel<UUIDv4Key>>
                } else {
                    super.readJson(reader, context)
                }
        }
    }
}
