package maryk.core.properties.enum

import maryk.core.definitions.PrimitiveType.TypeDefinition
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.descriptors.addDescriptorPropertyWrapperWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
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
    private val valueByIndex: Map<UInt, E> by lazy {
        cases().associate { Pair(it.index, it) }
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
        val name = add(1u, "name",
            StringDefinition(),
            MultiTypeEnumDefinition<*>::name
        )

        val cases = addDescriptorPropertyWrapperWrapper(2u, "cases")

        init {
            add(
                3u, "reservedIndices",
                ListDefinition(
                    valueDefinition = NumberDefinition(
                        type = UInt32,
                        minValue = 1u
                    )
                ),
                MultiTypeEnumDefinition<*>::reservedIndices
            )

            add(
                4u, "reservedNames",
                ListDefinition(
                    valueDefinition = StringDefinition()
                ),
                MultiTypeEnumDefinition<*>::reservedNames
            )
        }
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
