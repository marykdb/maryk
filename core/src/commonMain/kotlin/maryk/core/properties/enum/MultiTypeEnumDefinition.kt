package maryk.core.properties.enum

import maryk.core.definitions.PrimitiveType.TypeDefinition
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import kotlin.reflect.KClass

/** Enum Definitions with a [name] and [cases] with types */
open class MultiTypeEnumDefinition<E : MultiTypeEnum<*>> internal constructor(
    optionalCases: (() -> Array<E>)?,
    name: String,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    unknownCreator: ((UInt, String) -> E)? = null
) : AbstractIndexedEnumDefinition<E>(
    optionalCases, name, reservedIndices, reservedNames, unknownCreator
) {
    override val primitiveType = TypeDefinition

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByString: Map<String, E> by lazy<Map<String, E>> {
        mutableMapOf<String, E>().also { output ->
            for (type in cases()) {
                output[type.name] = type
                type.alternativeNames?.forEach { name: String ->
                    if (output.containsKey(name)) throw ParseException("Enum ${this.name} already has a case for $name")
                    output[name] = type
                }
            }
        }
    }

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByIndex by lazy {
        cases().associateBy { it.index }
    }

    override val cases get() = optionalCases!!

    constructor(
        enumClass: KClass<E>,
        values: () -> Array<E>,
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
        unknownCreator: ((UInt, String) -> E)? = null
    ) : this(
        name = enumClass.simpleName ?: throw DefNotFoundException("No name for enum class"),
        optionalCases = values,
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        unknownCreator = unknownCreator
    )

    internal constructor(
        name: String,
        values: () -> Array<E>,
        reservedIndices: List<UInt>? = null,
        reservedNames: List<String>? = null,
        unknownCreator: ((UInt, String) -> E)? = null
    ) : this(
        name = name,
        optionalCases = values,
        reservedIndices = reservedIndices,
        reservedNames = reservedNames,
        unknownCreator = unknownCreator
    )

    internal object Properties : ObjectPropertyDefinitions<MultiTypeEnumDefinition<MultiTypeEnum<*>>>() {
        val name by string(
            1u,
            MultiTypeEnumDefinition<*>::name
        )

        val cases = ContextualDefinitionWrapper<List<MultiTypeEnum<*>>, Array<MultiTypeEnum<*>>, MultiTypeDefinitionContext, ContextCollectionTransformerDefinition<MultiTypeEnum<*>, List<MultiTypeEnum<*>>, MultiTypeDefinitionContext, ContainsDefinitionsContext>, MultiTypeEnumDefinition<MultiTypeEnum<*>>>(
            2u, "cases",
            definition = ContextCollectionTransformerDefinition(
                definition = MultiTypeDescriptorListDefinition(),
                contextTransformer = { context: MultiTypeDefinitionContext? ->
                    context?.definitionsContext
                }
            ),
            toSerializable = { values: Array<MultiTypeEnum<*>>?, _ ->
                values?.toList()
            },
            fromSerializable = { values ->
                values?.toTypedArray()
            },
            getter = {
                it.cases()
            }
        ).apply {
            addSingle(this)
        }

        val reservedIndices by list(
            index = 3u,
            getter = MultiTypeEnumDefinition<*>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )

        val reservedNames by list(
            index = 4u,
            getter = MultiTypeEnumDefinition<*>::reservedNames,
            valueDefinition = StringDefinition()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiTypeEnumDefinition<*>) return false

        if (optionalCases != null) {
            return if (other.optionalCases != null) {
                other.optionalCases.invoke().contentEquals(optionalCases.invoke())
            } else false
        }
        if (name != other.name) return false
        if (reservedIndices != other.reservedIndices) return false
        if (reservedNames != other.reservedNames) return false

        return true
    }

    override fun hashCode(): Int {
        var result = optionalCases?.invoke().hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + reservedIndices.hashCode()
        result = 31 * result + reservedNames.hashCode()
        return result
    }

    override fun enumTypeIsCompatible(storedEnum: E, newEnum: E, addIncompatibilityReason: ((String) -> Unit)?): Boolean {
        return newEnum.definition!!.compatibleWith(storedEnum.definition!!, addIncompatibilityReason)
    }

    internal object Model :
        ContextualDataModel<MultiTypeEnumDefinition<MultiTypeEnum<*>>, Properties, ContainsDefinitionsContext, MultiTypeDefinitionContext>(
            properties = Properties,
            contextTransformer = { MultiTypeDefinitionContext(it) }
        ) {
        override fun invoke(values: ObjectValues<MultiTypeEnumDefinition<MultiTypeEnum<*>>, Properties>): MultiTypeEnumDefinition<MultiTypeEnum<*>> {
            return MultiTypeEnumDefinition(
                name = values(1u),
                optionalCases = values<Array<MultiTypeEnum<*>>?>(2u)?.let { { it } },
                reservedIndices = values(3u),
                reservedNames = values(4u),
                unknownCreator = { index, name -> MultiTypeEnum.invoke(index, name, null) }
            )
        }

        override fun writeJson(
            values: ObjectValues<MultiTypeEnumDefinition<MultiTypeEnum<*>>, Properties>,
            writer: IsJsonLikeWriter,
            context: MultiTypeDefinitionContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(
            obj: MultiTypeEnumDefinition<MultiTypeEnum<*>>,
            writer: IsJsonLikeWriter,
            context: MultiTypeDefinitionContext?
        ) {
            if (context?.definitionsContext?.typeEnums?.containsKey(obj.name) == true) {
                // Write a single string name if no options was defined
                val value = Properties.name.getPropertyAndSerialize(obj, context)
                    ?: throw ParseException("Missing requests in Requests")
                Properties.name.writeJsonValue(value, writer, context)
                Properties.name.capture(context, value)
            } else {
                // Only skip when DefinitionsContext was set
                when {
                    context?.definitionsContext != null && context.definitionsContext.currentDefinitionName == obj.name -> {
                        super.writeJson(obj, writer, context, skip = listOf(Properties.name))
                        context.definitionsContext.currentDefinitionName = ""
                    }
                    else -> {
                        super.writeJson(obj, writer, context)
                    }
                }
            }
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: MultiTypeDefinitionContext?
        ): ObjectValues<MultiTypeEnumDefinition<MultiTypeEnum<*>>, Properties> {
            if (reader.currentToken == StartDocument) {
                reader.nextToken()
            }

            return if (reader.currentToken is Value<*>) {
                val value = Properties.name.readJson(reader, context)
                Properties.name.capture(context, value)

                this.values {
                    mapNonNulls(
                        name withSerializable value
                    )
                }
            } else {
                super.readJson(reader, context)
            }
        }

        override fun readJsonToMap(reader: IsJsonLikeReader, context: MultiTypeDefinitionContext?) =
            context?.definitionsContext?.currentDefinitionName.let { name ->
                when (name) {
                    null, "" -> super.readJsonToMap(reader, context)
                    else -> {
                        context?.definitionsContext?.currentDefinitionName = ""
                        super.readJsonToMap(reader, context).also {
                            it[Properties.name.index] = name
                        }
                    }
                }
            }
    }
}

/**
 * Definition for Multi type descriptor List property.
 * Overrides ListDefinition so it can write special Yaml notation with complex field names.
 */
private class MultiTypeDescriptorListDefinition :
    maryk.core.properties.definitions.IsListDefinition<MultiTypeEnum<*>, IsPropertyContext> {
    override val required: Boolean = true
    override val final: Boolean = false
    override val minSize: UInt? = null
    override val maxSize: UInt? = null
    override val default: List<MultiTypeEnum<*>>? = null
    override val valueDefinition: IsValueDefinition<MultiTypeEnum<*>, IsPropertyContext> =
        EmbeddedObjectDefinition(
            dataModel = { MultiTypeEnum.Model }
        )

    /** Write [value] to JSON [writer] with [context] */
    override fun writeJsonValue(
        value: List<MultiTypeEnum<*>>,
        writer: IsJsonLikeWriter,
        context: IsPropertyContext?
    ) {
        if (writer is maryk.yaml.YamlWriter) {
            writer.writeStartObject()
            for (it in value) {
                this.valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndObject()
        } else {
            writer.writeStartArray()
            for (it in value) {
                this.valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndArray()
        }
    }

    /** Read Collection from JSON [reader] within optional [context] */
    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): List<MultiTypeEnum<*>> {
        val collection: MutableList<MultiTypeEnum<*>> = newMutableCollection(context)

        if (reader is IsYamlReader) {
            if (reader.currentToken !is maryk.json.JsonToken.StartObject) {
                throw ParseException("YAML definition map should be an Object")
            }

            while (reader.nextToken() !== maryk.json.JsonToken.EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        } else {
            if (reader.currentToken !is maryk.json.JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }
            while (reader.nextToken() !== maryk.json.JsonToken.EndArray) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        }
        return collection
    }
}
