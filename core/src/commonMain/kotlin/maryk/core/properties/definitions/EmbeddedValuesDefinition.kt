package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.AbstractValuesDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.DataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionType.Embed
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.safeLazy

/** Definition for embedded object properties [P] to [dataModel] of type [DM] */
class EmbeddedValuesDefinition<DM : IsValuesDataModel<P>, P : PropertyDefinitions>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    dataModel: Unit.() -> DM,
    override val default: Values<DM, P>? = null
) :
    IsEmbeddedValuesDefinition<DM, P, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Values<DM, P>> {
    override val propertyDefinitionType = Embed
    override val wireType = LENGTH_DELIMITED

    private val internalDataModel = safeLazy(dataModel)
    override val dataModel: DM get() = internalDataModel.value

    @Suppress("UNCHECKED_CAST")
    // internal strong typed version so type system is not in a loop when creating EmbeddedValuesDefinition
    internal val typedDataModel get() =
        internalDataModel.value as AbstractValuesDataModel<DM, P, IsPropertyContext>

    override fun asString(value: Values<DM, P>, context: IsPropertyContext?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: IsPropertyContext?): Values<DM, P> {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = dataModel.properties[name]

    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = dataModel.properties[index]

    override fun validateWithRef(
        previousValue: Values<DM, P>?,
        newValue: Values<DM, P>?,
        refGetter: () -> IsPropertyReference<Values<DM, P>, IsPropertyDefinition<Values<DM, P>>, *>?
    ) {
        super<IsEmbeddedValuesDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.typedDataModel.validate(
                values = newValue,
                refGetter = refGetter
            )
        }
    }

    override fun writeJsonValue(value: Values<DM, P>, writer: IsJsonLikeWriter, context: IsPropertyContext?) =
        this.typedDataModel.writeJson(
            value,
            writer,
            context
        )

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?) =
        this.typedDataModel.readJson(reader, context)

    override fun calculateTransportByteLength(
        value: Values<DM, P>,
        cacher: WriteCacheWriter,
        context: IsPropertyContext?
    ) =
        this.typedDataModel.calculateProtoBufLength(
            value,
            cacher,
            context
        )

    override fun writeTransportBytes(
        value: Values<DM, P>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        this.typedDataModel.writeProtoBuf(
            value,
            cacheGetter,
            writer,
            context
        )
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: Values<DM, P>?
    ) =
        this.typedDataModel.readProtoBuf(length, reader, context)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedValuesDefinition<*, *>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (internalDataModel.value != other.internalDataModel.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + internalDataModel.value.hashCode()
        return result
    }

    object Model :
        ContextualDataModel<EmbeddedValuesDefinition<*, *>, ObjectPropertyDefinitions<EmbeddedValuesDefinition<*, *>>, ContainsDefinitionsContext, ModelContext>(
            contextTransformer = { ModelContext(it) },
            properties = object : ObjectPropertyDefinitions<EmbeddedValuesDefinition<*, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, EmbeddedValuesDefinition<*, *>::required)
                    IsPropertyDefinition.addFinal(this, EmbeddedValuesDefinition<*, *>::final)
                    add(3u, "dataModel",
                        ContextualModelReferenceDefinition(
                            contextTransformer = { context: ModelContext? ->
                                context?.definitionsContext
                            },
                            contextualResolver = { context: ContainsDefinitionsContext?, name ->
                                context?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    it.dataModels[name] as? Unit.() -> DataModel<*, *>
                                        ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                                } ?: throw ContextNotFoundException()
                            }
                        ),
                        getter = {
                            { it.dataModel as DataModel<*, *> }
                        },
                        toSerializable = { value: (Unit.() -> DataModel<*, *>)?, _ ->
                            value?.invoke(Unit)?.let { model ->
                                DataModelReference(model.name, value)
                            }
                        },
                        fromSerializable = { it?.get },
                        capturer = { context: ModelContext, dataModel: IsDataModelReference<DataModel<*, *>> ->
                            context.definitionsContext?.let {
                                if (!it.dataModels.containsKey(dataModel.name)) {
                                    it.dataModels[dataModel.name] = dataModel.get
                                }
                            } ?: throw ContextNotFoundException()

                            context.model = dataModel.get
                        }
                    )

                    @Suppress("UNCHECKED_CAST")
                    add(4u, "default",
                        ContextualEmbeddedValuesDefinition(
                            contextualResolver = { context: ModelContext? ->
                                context?.model?.invoke(Unit) as? AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, ModelContext>?
                                    ?: throw ContextNotFoundException()
                            }
                        ) as IsEmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, ModelContext>,
                        EmbeddedValuesDefinition<*, *>::default as (EmbeddedValuesDefinition<*, *>) -> Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>?
                    )
                }
            }
        ) {
        override fun invoke(values: ObjectValues<EmbeddedValuesDefinition<*, *>, ObjectPropertyDefinitions<EmbeddedValuesDefinition<*, *>>>) =
            EmbeddedValuesDefinition<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                required = values(1u),
                final = values(2u),
                dataModel = values(3u),
                default = values(4u)
            )
    }
}
