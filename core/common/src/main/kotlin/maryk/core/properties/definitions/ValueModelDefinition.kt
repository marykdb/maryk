package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.models.ValueDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WireType
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

private typealias GenericValueModelDefinition = ValueModelDefinition<*, *, *>
//private typealias GenericValueModelDefinition2 = ValueModelDefinition<ValueDataObject, ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>>

/** Definition for value model properties containing dataObjects of [DO] defined by [dataModel] of [DM] */
data class ValueModelDefinition<DO: ValueDataObject, DM : ValueDataModel<DO, P>, P: ObjectPropertyDefinitions<DO>>(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    val dataModel: DM,
    override val minValue: DO? = null,
    override val maxValue: DO? = null,
    override val default: DO? = null
) :
    IsComparableDefinition<DO, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<DO, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<DO>,
    HasDefaultValueDefinition<DO>
{
    override val propertyDefinitionType = PropertyDefinitionType.Value
    override val wireType = WireType.LENGTH_DELIMITED
    override val byteSize = dataModel.byteSize

    override fun calculateStorageByteLength(value: DO) = this.byteSize

    override fun writeStorageBytes(value: DO, writer: (byte: Byte) -> Unit) = value._bytes.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        this.dataModel.readFromBytes(reader)

    override fun calculateTransportByteLength(value: DO) = this.dataModel.byteSize

    override fun asString(value: DO) = value.toBase64()

    override fun fromString(string: String) = this.dataModel.fromString(string)

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties.get(name)

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties[index]

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

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): DO = dataModel.readJson(reader, context).toDataObject()

    override fun fromNativeType(value: Any) =
        if(value is ByteArray && value.size == this.byteSize){
            var i = 0
            this.dataModel.readFromBytes {
                value[i++]
            }
        } else { null }

    object Model : ContextualDataModel<ValueModelDefinition<*, *, *>, ObjectPropertyDefinitions<ValueModelDefinition<*, *, *>>, DataModelContext, ModelContext>(
        contextTransformer = { ModelContext(it) },
        properties = object : ObjectPropertyDefinitions<ValueModelDefinition<*, *, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, ValueModelDefinition<*, *, *>::indexed)
                IsPropertyDefinition.addRequired(this, ValueModelDefinition<*, *, *>::required)
                IsPropertyDefinition.addFinal(this, ValueModelDefinition<*, *, *>::final)
                IsComparableDefinition.addUnique(this, ValueModelDefinition<*, *, *>::unique)

                add(4, "dataModel",
                    ContextualModelReferenceDefinition<ValueDataModel<*, *>,ModelContext, DataModelContext>(
                        contextTransformer = { it?.dataModelContext },
                        contextualResolver = { context, name ->
                            context?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.dataModels[name] as (() -> ValueDataModel<*, *>)? ?: throw DefNotFoundException("DataModel with name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModelDefinition<*, *, *>::dataModel,
                    toSerializable = { value: ValueDataModel<*, *>?, _ ->
                        value?.let{
                            DataModelReference(it.name){ it }
                        }
                    },
                    fromSerializable = {
                        it?.get?.invoke()
                    },
                    capturer = { context, dataModel ->
                        context.let {
                            context.dataModelContext?.let { modelContext ->
                                if (!modelContext.dataModels.containsKey(dataModel.name)) {
                                    modelContext.dataModels[dataModel.name] = dataModel.get
                                }
                            } ?: throw ContextNotFoundException()

                            @Suppress("UNCHECKED_CAST")
                            context.model = dataModel.get as () -> AbstractObjectDataModel<Any, ObjectPropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>
                        }
                    }
                )

                add(5, "minValue",
                    ContextualEmbeddedObjectDefinition(
                        contextualResolver = { context: ModelContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.model?.invoke() as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>? ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModelDefinition<*, *, *>::minValue
                )

                add(6, "maxValue",
                    ContextualEmbeddedObjectDefinition(
                        contextualResolver = { context: ModelContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.model?.invoke() as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>? ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModelDefinition<*, *, *>::maxValue
                )

                add(7, "default",
                    ContextualEmbeddedObjectDefinition(
                        contextualResolver = { context: ModelContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.model?.invoke() as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>> ?: throw ContextNotFoundException()
                        }
                    ),
                    ValueModelDefinition<*, *, *>::default
                )
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: SimpleObjectValues<ValueModelDefinition<*, *, *>>) = ValueModelDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            dataModel = map<ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>>(4),
            minValue = map(5),
            maxValue = map(6),
            default = map(7)
        ) as GenericValueModelDefinition
    }
}
