package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
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
import kotlin.reflect.KClass

/**
 * DataModel for objects which should be treated as a Value in total.
 * They will all together be serialized as a single byte value or string and can
 * be used as the key for a map.
 */
abstract class ValueDataModel<DO: ValueDataObject, DM: IsValueDataModel<DO, *>> internal constructor(
    name: String,
): TypedObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(), IsValueDataModel<DO, DM>, MarykPrimitive {
    constructor(
        objClass: KClass<DO>,
    ): this(
        objClass.simpleName ?: throw DefNotFoundException("RootDataModel should have a name. Please define in a class instead of anonymous object or pass name as property."),
    )

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    private val typedThis: DM = this as DM

    override val Serializer = object: ValueDataModelSerializer<DO, DM>(typedThis) {}

    override val Meta = ValueDataModelDefinition(name = name)

    abstract override fun invoke(values: ObjectValues<DO, DM>): DO

    override fun equals(other: Any?) =
        super.equals(other) && other is ValueDataModel<*, *> && this.Meta == other.Meta

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + Meta.hashCode()
        return result
    }

    fun toBytes(vararg inputs: Any) =
        Serializer.toBytes(*inputs)

    internal object Model: DefinitionModel<ValueDataModel<*, *>>() {
        val properties = ObjectDataModelCollectionDefinitionWrapper<ValueDataModel<*, *>>(
            1u,
            "properties",
            ObjectDataModelPropertiesCollectionDefinition(
                capturer = { context, propDefs ->
                    context?.apply {
                        this.propertyDefinitions = propDefs
                    } ?: throw ContextNotFoundException()
                }
            ),
            getter = {
                @Suppress("UNCHECKED_CAST")
                it as IsObjectDataModel<in Any>
            }
        ).also(this::addSingle)
        val meta = EmbeddedObjectDefinitionWrapper(
            2u,
            "meta",
            EmbeddedObjectDefinition(required = true, final = true, dataModel = { ValueDataModelDefinition.Model }),
            getter = ValueDataModel<*, *>::Meta,
        ).also(this::addSingle)

        override fun invoke(values: ObjectValues<ValueDataModel<*, *>, IsObjectDataModel<ValueDataModel<*, *>>>): ValueDataModel<*, *> {
            val meta: ValueDataModelDefinition = values(meta.index)

            return object : ValueDataModel<ValueDataObject, ValueDataModel<ValueDataObject, *>>(
                meta.name
            ) {
                override val Meta: ValueDataModelDefinition = meta

                override fun invoke(values: ObjectValues<ValueDataObject, ValueDataModel<ValueDataObject, *>>) =
                    ValueDataObjectWithValues(this.toBytes(values), values)
            }.also {
                values<Collection<AnyDefinitionWrapper>>(properties.index).forEach(it::addSingle)
            }
        }

        override val Serializer = object: ObjectDataModelSerializer<ValueDataModel<*, *>, IsObjectDataModel<ValueDataModel<*, *>>, ContainsDefinitionsContext, ContainsDefinitionsContext>(this) {
            override fun writeObjectAsJson(
                obj: ValueDataModel<*, *>,
                writer: IsJsonLikeWriter,
                context: ContainsDefinitionsContext?,
                skip: List<IsDefinitionWrapper<*, *, *, ValueDataModel<*, *>>>?
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
                    @Suppress("UNCHECKED_CAST")
                    for (property in obj as Iterable<AnyDefinitionWrapper>) {
                        properties.valueDefinition.writeJsonValue(property, writer, context)
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    this.writeJsonValue(
                        properties as IsDefinitionWrapper<in Any, in Any, IsPropertyContext, ValueDataModel<*, *>>,
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
                        if (values.contains(ValueDataModelDefinition.Model.name.index)) {
                            throw RequestException("Name $name was already defined by map")
                        }
                        // Reset it so no deeper value can reuse it
                        context.currentDefinitionName = ""

                        metaValues += ValueItem(ValueDataModelDefinition.Model.name.index, name)
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

                            when (val definition = ValueDataModelDefinition.Model[value]) {
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
                        ValueDataModelDefinition.Model,
                        ValueItems(*metaValues.toTypedArray()),
                    )
                }
            }
        }
    }
}
