package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeBytes
import maryk.core.objects.DefinitionDataModel
import maryk.core.objects.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WireType
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

/** Definition for value model properties containing dataObjects of [DO] defined by [dataModel] of [DM] */
data class ValueModelDefinition<DO: ValueDataObject, out DM : ValueDataModel<DO, PropertyDefinitions<DO>>>(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: DO? = null,
    override val maxValue: DO? = null,
    val dataModel: DM
) :
    IsComparableDefinition<DO, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<DO, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<DO>
{
    override val propertyDefinitionType = PropertyDefinitionType.ValueModel
    override val wireType = WireType.LENGTH_DELIMITED
    override val byteSize = dataModel.byteSize

    override fun calculateStorageByteLength(value: DO) = this.byteSize

    override fun writeStorageBytes(value: DO, writer: (byte: Byte) -> Unit) = value._bytes.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        this.dataModel.readFromBytes(reader)

    override fun calculateTransportByteLength(value: DO) = this.dataModel.byteSize

    override fun asString(value: DO) = value.toBase64()

    override fun fromString(string: String) = this.dataModel.fromString(string)

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties.getDefinition(name)

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties.getDefinition(index)

    override fun validateWithRef(previousValue: DO?, newValue: DO?, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        super<IsComparableDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                refGetter = refGetter,
                dataObject = newValue
            )
        }
    }

    /** Writes a [value] to JSON with [writer] */
    override fun writeJsonValue(value: DO, writer: IsJsonLikeWriter, context: IsPropertyContext?) = dataModel.writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): DO = dataModel.readJsonToObject(reader, context)

    override fun fromNativeType(value: Any) =
        if(value is ByteArray && value.size == this.byteSize){
            var i = 0
            this.dataModel.readFromBytes {
                value[i++]
            }
        } else { null }

    object Model : DefinitionDataModel<ValueModelDefinition<*, *>>(
        properties = object : PropertyDefinitions<ValueModelDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, ValueModelDefinition<*, *>::indexed)
                IsPropertyDefinition.addSearchable(this, ValueModelDefinition<*, *>::searchable)
                IsPropertyDefinition.addRequired(this, ValueModelDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, ValueModelDefinition<*, *>::final)
                IsComparableDefinition.addUnique(this, ValueModelDefinition<*, *>::unique)
                add(5, "minValue",
                    FlexBytesDefinition(),
                    ValueModelDefinition<*, *>::minValue,
                    { it?._bytes?.let { Bytes(it) } },
                    { it?.let { ValueDataObject(it.bytes) } }
                )
                add(6, "maxValue",
                    FlexBytesDefinition(),
                    ValueModelDefinition<*, *>::maxValue,
                    { it?._bytes?.let { Bytes(it) } },
                    { it?.let { ValueDataObject(it.bytes) } }
                )
                add(7, "dataModel",
                    ContextualModelReferenceDefinition<DataModelContext>(
                        contextualResolver = { context, name ->
                            context?.let {
                                it.dataModels[name] ?: throw DefNotFoundException("DataModel with name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModelDefinition<*, *>::dataModel,
                    capturer = { context, dataModel ->
                        context.let {
                            if (!it.dataModels.containsKey(dataModel.name)) {
                                it.dataModels[dataModel.name] = dataModel
                            }
                        }
                    }
                )
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = ValueModelDefinition(
            indexed = map(0),
            searchable = map(1),
            required = map(2),
            final = map(3),
            unique = map(4),
            minValue = map(5),
            maxValue = map(6),
            dataModel = map(7)
        )
    }
}
