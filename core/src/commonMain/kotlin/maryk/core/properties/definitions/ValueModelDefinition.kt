package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.models.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionType.Value
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.ValueDataObject
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter

private typealias GenericValueModelDefinition = ValueModelDefinition<*, *, *>
//private typealias GenericValueModelDefinition2 = ValueModelDefinition<ValueDataObject, ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>>

/** Definition for value model properties containing dataObjects of [DO] defined by [dataModel] of [DM] */
data class ValueModelDefinition<DO : ValueDataObject, DM : ValueDataModel<DO, P>, P : ObjectPropertyDefinitions<DO>>(
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
    HasDefaultValueDefinition<DO> {
    override val propertyDefinitionType = Value
    override val wireType = LENGTH_DELIMITED
    override val byteSize = dataModel.byteSize

    override fun calculateStorageByteLength(value: DO) = this.byteSize

    override fun writeStorageBytes(value: DO, writer: (byte: Byte) -> Unit) = value._bytes.writeBytes(writer)

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        this.dataModel.readFromBytes(reader)

    override fun calculateTransportByteLength(value: DO) = this.dataModel.byteSize

    override fun asString(value: DO) = value.toBase64()

    override fun fromString(string: String) = this.dataModel.fromBase64(string)

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = dataModel.properties[name]

    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = dataModel.properties[index]

    override fun validateWithRef(
        previousValue: DO?,
        newValue: DO?,
        refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>?
    ) {
        super<IsComparableDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                refGetter = refGetter,
                dataObject = newValue
            )
        }
    }

    /** Writes a [value] to JSON with [writer] */
    override fun writeJsonValue(value: DO, writer: IsJsonLikeWriter, context: IsPropertyContext?) =
        dataModel.writeJson(value, writer, context)

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): DO =
        dataModel.readJson(reader, context).toDataObject()

    override fun fromNativeType(value: Any) =
        if (value is ByteArray && value.size == this.byteSize) {
            var i = 0
            this.dataModel.readFromBytes {
                value[i++]
            }
        } else {
            null
        }

    object Model :
        ContextualDataModel<ValueModelDefinition<*, *, *>, ObjectPropertyDefinitions<ValueModelDefinition<*, *, *>>, ContainsDefinitionsContext, ModelContext>(
            contextTransformer = { ModelContext(it) },
            properties = object : ObjectPropertyDefinitions<ValueModelDefinition<*, *, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, ValueModelDefinition<*, *, *>::required)
                    IsPropertyDefinition.addFinal(this, ValueModelDefinition<*, *, *>::final)
                    IsComparableDefinition.addUnique(this, ValueModelDefinition<*, *, *>::unique)

                    add(4u, "dataModel",
                        ContextualModelReferenceDefinition<ValueDataModel<*, *>, ModelContext>(
                            contextualResolver = { context, name ->
                                context?.definitionsContext?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    it.dataModels[name] as (Unit.() -> ValueDataModel<*, *>)?
                                        ?: throw DefNotFoundException("DataModel with name $name not found on dataModels")
                                } ?: throw ContextNotFoundException()
                            }
                        ),
                        ValueModelDefinition<*, *, *>::dataModel,
                        toSerializable = { value: ValueDataModel<*, *>?, _ ->
                            value?.let {
                                DataModelReference(it.name) { it }
                            }
                        },
                        fromSerializable = {
                            it?.get?.invoke(Unit)
                        },
                        capturer = { context, dataModel ->
                            context.let {
                                context.definitionsContext?.let { modelContext ->
                                    if (!modelContext.dataModels.containsKey(dataModel.name)) {
                                        modelContext.dataModels[dataModel.name] = dataModel.get
                                    }
                                } ?: throw ContextNotFoundException()

                                @Suppress("UNCHECKED_CAST")
                                context.model =
                                    dataModel.get as Unit.() -> AbstractObjectDataModel<Any, ObjectPropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>
                            }
                        }
                    )

                    add(5u, "minValue",
                        ContextualEmbeddedObjectDefinition(
                            contextualResolver = { context: ModelContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.model?.invoke(Unit) as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>?
                                    ?: throw ContextNotFoundException()
                            }
                        ),
                        ValueModelDefinition<*, *, *>::minValue
                    )

                    add(6u, "maxValue",
                        ContextualEmbeddedObjectDefinition(
                            contextualResolver = { context: ModelContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.model?.invoke(Unit) as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>?
                                    ?: throw ContextNotFoundException()
                            }
                        ),
                        ValueModelDefinition<*, *, *>::maxValue
                    )

                    add(7u, "default",
                        ContextualEmbeddedObjectDefinition(
                            contextualResolver = { context: ModelContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.model?.invoke(Unit) as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>
                                    ?: throw ContextNotFoundException()
                            }
                        ),
                        ValueModelDefinition<*, *, *>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<ValueModelDefinition<*, *, *>>) = ValueModelDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            dataModel = values<ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>>(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u)
        ) as GenericValueModelDefinition
    }
}
