package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitiveDescriptor
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.ContextualDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.values
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SingleOrListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import kotlin.reflect.KClass

/** Enum Definitions with a [name] and [cases] */
open class IndexedEnumDefinition<E : IndexedEnum> internal constructor(
    optionalCases: (() -> List<E>)?,
    name: String,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    unknownCreator: ((UInt, String) -> E)? = null
) : AbstractIndexedEnumDefinition<E>(
    optionalCases, name, reservedIndices, reservedNames, unknownCreator
) {
    constructor(
        enumClass: KClass<E>,
        values: () -> List<E>,
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
        values: () -> List<E>,
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

    override val Meta = object: MarykPrimitiveDescriptor {
        override val name: String = this@IndexedEnumDefinition.name
        override val primitiveType = PrimitiveType.EnumDefinition
    }

    internal object Model : ContextualDataModel<IndexedEnumDefinition<IndexedEnum>, Model, ContainsDefinitionsContext, EnumNameContext>(
        contextTransformer = { EnumNameContext(it) }
    ) {
        val name by contextual(
            index = 1u,
            getter = IndexedEnumDefinition<*>::name,
            definition = ContextCaptureDefinition(
                definition = StringDefinition(),
                capturer = { context: EnumNameContext?, value ->
                    context?.let {
                        it.name = value
                    }
                }
            )
        )

        val cases by map(
            index = 2u,
            getter = IndexedEnumDefinition<*>::cases,
            keyDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            ),
            valueDefinition = SingleOrListDefinition(
                valueDefinition = StringDefinition()
            ),
            toSerializable = { value: (() -> List<IndexedEnum>?)?, context: EnumNameContext? ->
                // If Enum was defined before and is thus available in context, don't include the cases again
                val toReturnNull = context?.let { enumNameContext ->
                    if (enumNameContext.isOriginalDefinition == true) {
                        false
                    } else {
                        enumNameContext.definitionsContext?.let {
                            if (it.enums[enumNameContext.name] == null) {
                                enumNameContext.isOriginalDefinition = true
                                false
                            } else {
                                true
                            }
                        }
                    }
                } == true

                if (toReturnNull) {
                    null
                } else {
                    // Combine name and alternative names into a list
                    value?.invoke()?.associate { v: IndexedEnum ->
                        // Combine name and alternative names into a list
                        val names: List<String> = v.alternativeNames?.toMutableList()?.also {
                            it.add(0, v.name)
                        } ?: listOf(v.name)
                        Pair(v.index, names)
                    }
                }
            },
            fromSerializable = {
                {
                    it?.map { (key, value) ->
                        IndexedEnumComparable(
                            key,
                            value.first(),
                            value.subList(1, value.size).toSet()
                        )
                    }
                }
            }
        )

        val reservedIndices by list(
            index = 3u,
            getter = IndexedEnumDefinition<*>::reservedIndices,
            valueDefinition = NumberDefinition(
                type = UInt32,
                minValue = 1u
            )
        )

        val reservedNames by list(
            index = 4u,
            getter = IndexedEnumDefinition<*>::reservedNames,
            valueDefinition = StringDefinition()
        )

        override fun invoke(values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Model>): IndexedEnumDefinition<IndexedEnum> =
            IndexedEnumDefinition(
                name = values(1u),
                optionalCases = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u),
                unknownCreator = { index, name -> IndexedEnumComparable(index, name) }
            )

        override val Serializer = object: ObjectDataModelSerializer<IndexedEnumDefinition<IndexedEnum>, Model, ContainsDefinitionsContext, EnumNameContext>(this) {
            override fun writeJson(
                values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Model>,
                writer: IsJsonLikeWriter,
                context: EnumNameContext?
            ) {
                throw SerializationException("Cannot write definitions from Values")
            }

            override fun writeObjectAsJson(
                obj: IndexedEnumDefinition<IndexedEnum>,
                writer: IsJsonLikeWriter,
                context: EnumNameContext?,
                skip: List<IsDefinitionWrapper<*, *, *, IndexedEnumDefinition<IndexedEnum>>>?
            ) {
                if (context?.definitionsContext?.enums?.containsKey(obj.name) == true) {
                    // Write a single string name if no options was defined
                    val value = name.getPropertyAndSerialize(obj, context)
                        ?: throw ParseException("Missing requests in Requests")
                    name.writeJsonValue(value, writer, context)
                    name.capture(context, value)
                } else {
                    // Only skip when DefinitionsContext was set
                    when {
                        context?.definitionsContext != null && context.definitionsContext.currentDefinitionName == obj.name -> {
                            super.writeObjectAsJson(obj, writer, context, skip = listOf(name) + skip.orEmpty())
                            context.definitionsContext.currentDefinitionName = ""
                        }
                        else -> {
                            super.writeObjectAsJson(obj, writer, context, skip)
                        }
                    }
                }
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: EnumNameContext?
            ): ObjectValues<IndexedEnumDefinition<IndexedEnum>, Model> {
                if (reader.currentToken == JsonToken.StartDocument) {
                    reader.nextToken()
                }

                return if (reader.currentToken is JsonToken.Value<*>) {
                    val value = name.readJson(reader, context)
                    name.capture(context, value)

                    values {
                        mapNonNulls(
                            this@Model.name withSerializable value
                        )
                    }
                } else {
                    super.readJson(reader, context)
                }
            }

            override fun readJsonToMap(reader: IsJsonLikeReader, context: EnumNameContext?) =
                context?.definitionsContext?.currentDefinitionName.let { name ->
                    when (name) {
                        null, "" -> super.readJsonToMap(reader, context)
                        else -> {
                            context?.definitionsContext?.currentDefinitionName = ""
                            super.readJsonToMap(reader, context).also {
                                it[this@Model.name.index] = name
                            }
                        }
                    }
                }
        }
    }
}
