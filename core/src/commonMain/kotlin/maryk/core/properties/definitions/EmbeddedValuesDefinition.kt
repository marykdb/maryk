package maryk.core.properties.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.validate
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitionType.Embed
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.contextual.embedContextual
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
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

/** Definition for embedded object to [dataModel] of type [DM] */
class EmbeddedValuesDefinition<DM : IsValuesDataModel>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    dataModel: () -> DM,
    override val default: Values<DM>? = null
) :
    IsEmbeddedValuesDefinition<DM, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Values<DM>> {
    override val propertyDefinitionType = Embed
    override val wireType = LENGTH_DELIMITED

    override val dataModel: DM by lazy(dataModel)

    @Suppress("UNCHECKED_CAST")
    // internal strong typed version so type system is not in a loop when creating EmbeddedValuesDefinition
    private val typedDataModel get() = dataModel as TypedValuesDataModel<DM>

    override fun asString(value: Values<DM>, context: IsPropertyContext?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: IsPropertyContext?): Values<DM> {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = dataModel[name]

    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = dataModel[index]

    override fun validateWithRef(
        previousValue: Values<DM>?,
        newValue: Values<DM>?,
        refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>?
    ) {
        super<IsEmbeddedValuesDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                values = newValue,
                refGetter = refGetter
            )
        }
    }

    override fun writeJsonValue(value: Values<DM>, writer: IsJsonLikeWriter, context: IsPropertyContext?) =
        this.typedDataModel.Serializer.writeJson(
            value,
            writer,
            context
        )

    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?) =
        this.typedDataModel.Serializer.readJson(reader, context)

    override fun calculateTransportByteLength(
        value: Values<DM>,
        cacher: WriteCacheWriter,
        context: IsPropertyContext?
    ) =
        this.typedDataModel.Serializer.calculateProtoBufLength(
            value,
            cacher,
            context
        )

    override fun writeTransportBytes(
        value: Values<DM>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        this.typedDataModel.Serializer.writeProtoBuf(
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
        earlierValue: Values<DM>?
    ) =
        this.typedDataModel.Serializer.readProtoBuf(length, reader, context)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedValuesDefinition<*>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (dataModel != other.dataModel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + dataModel.hashCode()
        return result
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        addDataModelToDependencySet(dependencySet)
    }

    object Model : ContextualDataModel<EmbeddedValuesDefinition<*>, Model, ContainsDefinitionsContext, ModelContext>(
        contextTransformer = { ModelContext(it) },
    ) {
        val required by boolean(1u, EmbeddedValuesDefinition<*>::required, default = true)
        val final by boolean(2u, EmbeddedValuesDefinition<*>::final, default = false)
        val dataModel by contextual(
            index = 3u,
            definition = ContextualModelReferenceDefinition(
                contextTransformer = { context: ModelContext? ->
                    context?.definitionsContext
                },
                contextualResolver = { context: ContainsDefinitionsContext?, name ->
                    context?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.dataModels[name] as? IsDataModelReference<IsValuesDataModel>
                            ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                    } ?: throw ContextNotFoundException()
                }
            ),
            getter = {
                { it.dataModel }
            },
            toSerializable = { value: (() -> IsValuesDataModel)?, _ ->
                value?.invoke()?.let { model ->
                    DataModelReference(model.Meta.name) { model }
                }
            },
            fromSerializable = {
                it?.get
            },
            capturer = { context: ModelContext, dataModel: IsDataModelReference<IsValuesDataModel> ->
                context.definitionsContext?.let {
                    if (!it.dataModels.containsKey(dataModel.name)) {
                        it.dataModels[dataModel.name] = dataModel
                    }
                } ?: throw ContextNotFoundException()

                context.model = dataModel.get
            }
        )

        @Suppress("UNCHECKED_CAST")
        val default by embedContextual(
            index = 4u,
            getter = EmbeddedValuesDefinition<*>::default,
            contextualResolver = { context: ModelContext? ->
                context?.model?.invoke() as? TypedValuesDataModel<IsValuesDataModel>
                    ?: throw ContextNotFoundException()
            }
        )

        override fun invoke(values: ObjectValues<EmbeddedValuesDefinition<*>, Model>) =
            EmbeddedValuesDefinition<IsValuesDataModel>(
                required = values(1u),
                final = values(2u),
                dataModel = values(3u),
                default = values(4u)
            )
    }
}

fun <DM : IsValuesDataModel> IsValuesDataModel.embed(
    index: UInt,
    dataModel: () -> DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: Values<DM>? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    EmbeddedValuesDefinitionWrapper(
        index,
        name ?: propName,
        EmbeddedValuesDefinition(required, final, dataModel, default),
        alternativeNames
    )
}

fun <DM : IsValuesDataModel> IsObjectDataModel<Any>.embed(
    index: UInt,
    getter: (Any) -> Values<DM>? = { null },
    dataModel: () -> DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: Values<DM>? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((Values<DM>?, IsPropertyContext?) -> Values<DM>?)? = null,
    fromSerializable: ((Values<DM>?) -> Values<DM>?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((IsPropertyContext, Values<DM>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    EmbeddedValuesDefinitionWrapper(
        index,
        name ?: propName,
        EmbeddedValuesDefinition(required, final, dataModel, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
