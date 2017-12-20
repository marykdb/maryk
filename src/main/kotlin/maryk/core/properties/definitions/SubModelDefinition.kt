package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.objects.AbstractDataModel
import maryk.core.objects.ContextualDataModel
import maryk.core.objects.DataModel
import maryk.core.objects.DefinitionDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualModelReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DataModelContext

/**
 * Definition for sub model properties
 * @param dataModel definition of the DataObject
 * @param <DM>  Type of model for this definition
 * @param <DO> Type of DataObject which is contained within SubModel
 */
class SubModelDefinition<DO : Any, out P: PropertyDefinitions<DO>, out DM : AbstractDataModel<DO, P, CXI, CX>, in CXI: IsPropertyContext, CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        dataModel: () -> DM
) :
        IsValueDefinition<DO, CXI>,
        IsSerializableFlexBytesEncodable<DO, CXI>,
        IsSubModelDefinition<DO, CXI>,
        IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.SubModel
    override val wireType = WireType.LENGTH_DELIMITED

    private val internalDataModel = lazy(dataModel)
    val dataModel: DM get() = internalDataModel.value

    override fun asString(value: DO, context: CXI?): String {
        var string = ""
        this.writeJsonValue(value, maryk.core.json.JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CXI?): DO {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = dataModel.getDefinition(name)

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = dataModel.getDefinition(index)

    override fun validateWithRef(previousValue: DO?, newValue: DO?, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        super<IsValueDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            this.dataModel.validate(
                    refGetter = refGetter,
                    dataObject = newValue
            )
        }
    }

    override fun writeJsonValue(value: DO, writer: JsonWriter, context: CXI?) = this.dataModel.writeJson(
            value,
            writer,
            this.dataModel.transformContext(context)
    )

    override fun readJson(reader: JsonReader, context: CXI?): DO {
        return this.dataModel.readJsonToObject(reader, this.dataModel.transformContext(context))
    }

    override fun calculateTransportByteLength(value: DO, cacher: WriteCacheWriter, context: CXI?): Int {
        var totalByteLength = 0
        val newContext = if (this.dataModel is ContextualDataModel<*, *, *, *>) {
            this.dataModel.transformContext(context)!!.apply {
                cacher.addContextToCache(this)
            }
        } else {
            @Suppress("UNCHECKED_CAST")
            context as CX?
        }
        totalByteLength += this.dataModel.calculateProtoBufLength(value, cacher, newContext)
        return totalByteLength
    }

    override fun writeTransportBytes(value: DO, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CXI?) {
        @Suppress("UNCHECKED_CAST")
        val newContext = if (this.dataModel is ContextualDataModel<*, *, *, *>) {
            cacheGetter.nextContextFromCache() as CX?
        } else {
            context as CX?
        }

        this.dataModel.writeProtoBuf(value, cacheGetter, writer, newContext)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CXI?)
            = this.dataModel.readProtoBufToObject(length, reader, this.dataModel.transformContext(context))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubModelDefinition<*, *, *, *, *>) return false

        if (indexed != other.indexed) return false
        if (searchable != other.searchable) return false
        if (required != other.required) return false
        if (final != other.final) return false
        if (internalDataModel.value != other.internalDataModel.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indexed.hashCode()
        result = 31 * result + searchable.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + internalDataModel.value.hashCode()
        return result
    }

    object Model : DefinitionDataModel<SubModelDefinition<*, *, *, *, *>>(
            properties = object : PropertyDefinitions<SubModelDefinition<*, *, *, *, *>>() {
                init {
                    IsPropertyDefinition.addIndexed(this, SubModelDefinition<*, *, *, *, *>::indexed)
                    IsPropertyDefinition.addSearchable(this, SubModelDefinition<*, *, *, *, *>::searchable)
                    IsPropertyDefinition.addRequired(this, SubModelDefinition<*, *, *, *, *>::required)
                    IsPropertyDefinition.addFinal(this, SubModelDefinition<*, *, *, *, *>::final)
                    add(4, "dataModel", ContextCaptureDefinition(
                            definition = ContextualModelReferenceDefinition<DataModelContext>(
                                    contextualResolver = { context, name ->
                                        context!!.dataModels[name]!!
                                    }
                            ),
                            capturer = { context, dataModel ->
                                if (!context!!.dataModels.containsKey(dataModel.name)) {
                                    context.dataModels[dataModel.name] = dataModel
                                }
                            }
                    )) {
                        it.dataModel as DataModel<*, *>
                    }
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = SubModelDefinition(
                indexed = map[0] as Boolean,
                searchable = map[1] as Boolean,
                required = map[2] as Boolean,
                final = map[3] as Boolean,
                dataModel = {
                    @Suppress("UNCHECKED_CAST")
                    map[4] as DataModel<Any, PropertyDefinitions<Any>>
                }
        )
    }
}