package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsStorableDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.safeLazy

/** Definition for embedded object properties to [dataModel] of type [DM] returning dataObject of [DO] */
class EmbeddedObjectDefinition<DO : Any, DM : IsTypedObjectDataModel<DO, *, CXI, CX>, CXI : IsPropertyContext, CX : IsPropertyContext>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    dataModel: Unit.() -> DM,
    override val default: DO? = null
) :
    IsUsableInMultiType<DO, CXI>,
    IsEmbeddedObjectDefinition<DO, DM, CXI, CX> {
    override val wireType = LENGTH_DELIMITED

    override val dataModel: DM by safeLazy(dataModel)

    override fun asString(value: DO, context: CXI?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CXI?): DO {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = dataModel[name]

    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = dataModel[index]

    override fun validateWithRef(
        previousValue: DO?,
        newValue: DO?,
        refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>?
    ) {
        super<IsEmbeddedObjectDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                refGetter = refGetter,
                dataObject = newValue
            )
        }
    }

    override fun writeJsonValue(value: DO, writer: IsJsonLikeWriter, context: CXI?) = this.dataModel.Serializer.writeObjectAsJson(
        value,
        writer,
        this.dataModel.Serializer.transformContext(context)
    )

    override fun calculateTransportByteLength(value: DO, cacher: WriteCacheWriter, context: CXI?) =
        this.dataModel.Serializer.calculateObjectProtoBufLength(
            value,
            cacher,
            dataModel.Serializer.transformContext(context)?.apply {
                if (context !== this) {
                    cacher.addContextToCache(this)
                }
            }
        )

    override fun writeTransportBytes(
        value: DO,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CXI?
    ) {
        @Suppress("UNCHECKED_CAST")
        (this.dataModel.Serializer as ObjectDataModelSerializer<DO, DM, CXI, CX>).writeObjectProtoBuf(
            value,
            cacheGetter,
            writer,
            getTransformedContextFromCache(cacheGetter, context)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTransformedContextFromCache(cacheGetter: WriteCacheReader, context: CXI?) =
        if (dataModel is ContextualDataModel<*, *, *, *>) {
            cacheGetter.nextContextFromCache() as CX?
        } else {
            context as CX?
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedObjectDefinition<*, *, *, *>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (dataModel.Serializer != other.dataModel.Serializer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + dataModel.hashCode()
        return result
    }

    object Model : ContextualDataModel<EmbeddedObjectDefinition<*, *, *, *>, Model, ContainsDefinitionsContext, ModelContext>(
        contextTransformer = { ModelContext(it) }
    ) {
        val required by boolean(1u, EmbeddedObjectDefinition<*, *, *, *>::required, default = true)
        val final by boolean(2u, EmbeddedObjectDefinition<*, *, *, *>::final, default = false)
        val dataModel by contextual(
            index = 3u,
            definition = ContextualModelReferenceDefinition(
                contextualResolver = { context: ModelContext?, name ->
                    context?.definitionsContext?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.dataModels[name] as? Unit.() -> IsTypedObjectDataModel<*, *, *, *>
                            ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                    } ?: throw ContextNotFoundException()
                }
            ),
            getter = {
                { it.dataModel }
            },
            toSerializable = { value: (Unit.() -> IsTypedObjectDataModel<*, *, *, *>)?, _ ->
                value?.invoke(Unit)?.let { model ->
                    DataModelReference((model as IsStorableDataModel<*>).Meta.name) { model }
                }
            },
            fromSerializable = { ref ->
                ref?.get
            },
            capturer = { context: ModelContext, dataModel: IsDataModelReference<IsTypedObjectDataModel<*, *, *, *>> ->
                context.definitionsContext?.let {
                    if (!it.dataModels.containsKey(dataModel.name)) {
                        it.dataModels[dataModel.name] = dataModel.get
                    }
                } ?: throw ContextNotFoundException()

                context.model = dataModel.get
            }
        )

        val default by contextual(
            index = 4u,
            getter = EmbeddedObjectDefinition<*, *, *, *>::default,
            definition = ContextualEmbeddedObjectDefinition(
                contextualResolver = { context: ModelContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.model?.invoke(Unit) as? IsTypedObjectDataModel<Any, *, *, ModelContext>
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<EmbeddedObjectDefinition<*, *, *, *>, Model>): EmbeddedObjectDefinition<*, *, *, *> = EmbeddedObjectDefinition<Any, IsTypedObjectDataModel<Any, *, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>(
            required = values(1u),
            final = values(2u),
            dataModel = values(3u),
            default = values(4u)
        )
    }
}

fun <DO : Any, P : IsTypedObjectDataModel<DO, IsObjectDataModel<DO>, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> IsValuesDataModel.embedObject(
    index: UInt,
    dataModel: Unit.() -> P,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: DO? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    EmbeddedObjectDefinitionWrapper<DO, DO, IsTypedObjectDataModel<DO, *, CXI, CX>, CXI, CX, Any>(
        index,
        name ?: propName,
        EmbeddedObjectDefinition(required, final, dataModel, default),
        alternativeNames
    )
}

fun <TO: Any, DO: Any, EDO : Any, DM : IsTypedObjectDataModel<EDO, DM, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> IsObjectDataModel<DO>.embedObject(
    index: UInt,
    getter: (DO) -> TO?,
    dataModel: Unit.() -> DM,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: EDO? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CXI?) -> EDO?)? = null,
    fromSerializable: (Unit.(EDO?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CXI, EDO) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    EmbeddedObjectDefinitionWrapper(
        index,
        name ?: propName,
        EmbeddedObjectDefinition(required, final, dataModel, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
