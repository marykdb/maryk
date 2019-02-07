package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.ContextualDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.lib.safeLazy

/** Definition for embedded object properties to [dataModel] of type [DM] returning dataObject of [DO] */
class EmbeddedObjectDefinition<DO : Any, P: ObjectPropertyDefinitions<DO>, out DM : AbstractObjectDataModel<DO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext>(
    override val required: Boolean = true,
    override val final: Boolean = false,
    dataModel: Unit.() -> DM,
    override val default: DO? = null
) :
    IsEmbeddedObjectDefinition<DO, P, DM, CXI, CX>
{
    override val propertyDefinitionType = PropertyDefinitionType.EmbedObject
    override val wireType = WireType.LENGTH_DELIMITED

    private val internalDataModel = safeLazy(dataModel)
    override val dataModel: DM get() = internalDataModel.value

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

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties[name]

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *, *>? = dataModel.properties[index]

    override fun validateWithRef(previousValue: DO?, newValue: DO?, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>, *>?) {
        super.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                refGetter = refGetter,
                dataObject = newValue
            )
        }
    }

    override fun writeJsonValue(value: DO, writer: IsJsonLikeWriter, context: CXI?) = this.dataModel.writeJson(
        value,
        writer,
        this.dataModel.transformContext(context)
    )

    override fun calculateTransportByteLength(value: DO, cacher: WriteCacheWriter, context: CXI?) =
        this.dataModel.calculateProtoBufLength(
            value,
            cacher,
            transformContext(context, cacher)
        )

    private fun transformContext(context: CXI?, cacher: WriteCacheWriter) =
        if (dataModel is ContextualDataModel<*, *, *, *>) {
            dataModel.transformContext(context)?.apply {
                cacher.addContextToCache(this)
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            context as CX?
        }

    override fun writeTransportBytes(
        value: DO,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CXI?
    ) {
        this.dataModel.writeProtoBuf(
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
        if (other !is EmbeddedObjectDefinition<*, *, *, *, *>) return false

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

    object Model : ContextualDataModel<EmbeddedObjectDefinition<*, *, *, *, *>, ObjectPropertyDefinitions<EmbeddedObjectDefinition<*, *, *, *, *>>, ContainsDefinitionsContext, ModelContext>(
        contextTransformer = { ModelContext(it) },
        properties = object : ObjectPropertyDefinitions<EmbeddedObjectDefinition<*, *, *, *, *>>() {
            init {
                IsPropertyDefinition.addRequired(this, EmbeddedObjectDefinition<*, *, *, *, *>::required)
                IsPropertyDefinition.addFinal(this, EmbeddedObjectDefinition<*, *, *, *, *>::final)
                add(3, "dataModel",
                    ContextualModelReferenceDefinition(
                        contextualResolver = { context: ModelContext?, name ->
                            context?.definitionsContext?.let{
                                @Suppress("UNCHECKED_CAST")
                                it.dataModels[name] as? Unit.() -> ObjectDataModel<*, *>
                                        ?: throw DefNotFoundException("ObjectDataModel of name $name not found on dataModels")
                            } ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = {
                        { it.dataModel as ObjectDataModel<*, *> }
                    },
                    toSerializable = { value: (Unit.() -> ObjectDataModel<*, *>)?, _ ->
                        value?.invoke(Unit)?.let{ model ->
                            DataModelReference(model.name, value)
                        }
                    },
                    fromSerializable = { it?.get },
                    capturer = { context: ModelContext, dataModel: IsDataModelReference<ObjectDataModel<*, *>> ->
                        context.definitionsContext?.let {
                            if (!it.dataModels.containsKey(dataModel.name)) {
                                it.dataModels[dataModel.name] = dataModel.get
                            }
                        } ?: throw ContextNotFoundException()

                        @Suppress("UNCHECKED_CAST")
                        context.model = dataModel.get as Unit.() -> AbstractObjectDataModel<Any, ObjectPropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>
                    }
                )

                add(4, "default",
                    ContextualEmbeddedObjectDefinition(
                        contextualResolver = { context: ModelContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.model?.invoke(Unit) as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>? ?: throw ContextNotFoundException()
                        }
                    ),
                    EmbeddedObjectDefinition<*, *, *, *, *>::default
                )
            }
        }
    ) {
        override fun invoke(values: ObjectValues<EmbeddedObjectDefinition<*, *, *, *, *>, ObjectPropertyDefinitions<EmbeddedObjectDefinition<*, *, *, *, *>>>) = EmbeddedObjectDefinition(
            required = values(1),
            final = values(2),
            dataModel = values<Unit.() -> ObjectDataModel<Any, ObjectPropertyDefinitions<Any>>>(3),
            default = values(4)
        )
    }
}
