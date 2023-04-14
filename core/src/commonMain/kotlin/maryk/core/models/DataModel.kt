package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.definitions.DataModelDefinition
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.core.values.ValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.YamlWriter

/**
 * Base class for regular DataModels which work with Values objects.
 * These DataModels can be embedded within other DataModels and RootDataModels.
 */
open class DataModel<DM: IsValuesDataModel>(
    meta: (String?) -> DataModelDefinition,
) : TypedValuesDataModel<DM>(), MarykPrimitive {
    constructor(
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
    ): this({ passedName ->
        DataModelDefinition(
            name = passedName ?: throw DefNotFoundException("DataModel should have a name. Please define in a class instead of anonymous object or pass name as property."),
            reservedIndices = reservedIndices,
            reservedNames = reservedNames,
        )
    })

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    private val typedThis: DM = this as DM

    override val Meta = meta(typedThis::class.simpleName)

    operator fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> invoke(
        parent: AnyOutPropertyReference? = null,
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ) = referenceGetter(typedThis)(parent)

    operator fun <R> invoke(block: DM.() -> R): R {
        return block(typedThis)
    }

    override fun equals(other: Any?) =
        super.equals(other) && other is DataModel<*> && this.Meta == other.Meta

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + Meta.hashCode()
        return result
    }

    object Model: DefinitionModel<DataModel<*>>() {
        val properties = DataModelCollectionDefinitionWrapper<Any>(
            1u,
            "properties",
            DataModelPropertiesCollectionDefinition(
                false,
                capturer = { context, propDefs ->
                    context?.apply {
                        this.propertyDefinitions = propDefs
                    } ?: throw ContextNotFoundException()
                }
            ),
            getter = { it as DataModel<*> }
        ).also(this::addSingle)
        val meta = EmbeddedObjectDefinitionWrapper(
            2u,
            "meta",
            EmbeddedObjectDefinition(required = true, final = true, dataModel = { DataModelDefinition.Model }),
            getter = DataModel<*>::Meta,
        ).also(this::addSingle)

        override fun invoke(values: ObjectValues<DataModel<*>, IsObjectDataModel<DataModel<*>>>): DataModel<*> =
            DataModel<IsValuesDataModel>(meta = { values(meta.index) }).also {
                values<Collection<AnyDefinitionWrapper>>(properties.index).forEach(it::addSingle)
            }

        override val Serializer = object: ObjectDataModelSerializer<DataModel<*>, IsObjectDataModel<DataModel<*>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeObjectAsJson(
                obj: DataModel<*>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, DataModel<*>>>?
            ) {
                writer.writeStartObject()
                for (def in meta.dataModel) {
                    // Skip name if defined higher
                    if (def == meta.dataModel.name && context != null && context.currentDefinitionName == obj.Meta.name) {
                        context.currentDefinitionName = "" // Reset after use
                        continue
                    }
                    val value = def.getPropertyAndSerialize(obj.Meta, context) ?: continue
                    writer.writeFieldName(def.name)
                    def.writeJsonValue(value, writer, context)
                }
                if (writer is YamlWriter) {
                    // Write optimized format when writing yaml
                    for (property in obj) {
                        properties.valueDefinition.writeJsonValue(property, writer, context)
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    this.writeJsonValue(
                        properties as IsDefinitionWrapper<in Any, in Any, IsPropertyContext, DataModel<*>>,
                        writer,
                        obj,
                        context
                    )
                }
                writer.writeEndObject()
            }

            override fun walkJsonToRead(
                reader: IsJsonLikeReader,
                values: MutableValueItems,
                context: ContainsDefinitionsContext?
            ) {
                val deserializedProperties = mutableListOf<AnyDefinitionWrapper>()
                val metaValues = mutableListOf<ValueItem>()

                // Inject name if it was defined as a map key in a higher level
                context?.currentDefinitionName?.let { name ->
                    if (name.isNotBlank()) {
                        if (values.contains(DataModelDefinition.Model.name.index)) {
                            throw RequestException("Name $name was already defined by map")
                        }
                        // Reset it so no deeper value can reuse it
                        context.currentDefinitionName = ""

                        metaValues += ValueItem(DataModelDefinition.Model.name.index, name)
                    }
                }

                walker@ do {
                    val token = reader.currentToken

                    when (token) {
                        is JsonToken.StartComplexFieldName -> {
                            deserializedProperties += properties.valueDefinition.readJson(reader, context)
                        }
                        is JsonToken.FieldName -> {
                            val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                            val definition = DataModelDefinition.Model[value]
                            when (definition) {
                                null -> {
                                    if (value == properties.name) {
                                        reader.nextToken() // continue for field name
                                        deserializedProperties += properties.readJson(reader, context as DefinitionsConversionContext)
                                    } else {
                                        reader.skipUntilNextField()
                                        continue@walker
                                    }
                                }
                                else -> {
                                    reader.nextToken()
                                    metaValues += ValueItem(definition.index, definition.definition.readJson(reader, context))
                                }
                            }
                        }
                        else -> break@walker
                    }
                    reader.nextToken()
                } while (token !is JsonToken.Stopped)

                if (model.isNotEmpty()) {
                    values[properties.index] = deserializedProperties
                }
                if (metaValues.isNotEmpty()) {
                    values[meta.index] = ObjectValues(
                        DataModelDefinition.Model,
                        ValueItems(*metaValues.toTypedArray()),
                    )
                }
            }
        }
    }
}
