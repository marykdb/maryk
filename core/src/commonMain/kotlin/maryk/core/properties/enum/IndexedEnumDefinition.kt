package maryk.core.properties.enum

import maryk.core.definitions.PrimitiveType.EnumDefinition
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SingleOrListDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import kotlin.reflect.KClass

/** Enum Definitions with a [name] and [cases] */
open class IndexedEnumDefinition<E : IndexedEnum> internal constructor(
    optionalCases: (() -> Array<E>)?,
    name: String,
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    unknownCreator: ((UInt, String) -> E)? = null
) : AbstractIndexedEnumDefinition<E>(
    optionalCases, name, reservedIndices, reservedNames, unknownCreator
) {
    override val primitiveType = EnumDefinition

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

    internal object Properties : ObjectPropertyDefinitions<IndexedEnumDefinition<IndexedEnum>>() {
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
            toSerializable = { value, context: EnumNameContext? ->
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
                } ?: false

                if (toReturnNull) {
                    null
                } else {
                    value?.invoke()?.map { v: IndexedEnum ->
                        // Combine name and alternative names into a list
                        val names: List<String> = v.alternativeNames?.toMutableList()?.also {
                            it.add(0, v.name)
                        } ?: listOf(v.name)
                        Pair(v.index, names)
                    }?.toMap() as Map<UInt, List<String>>
                }
            },
            fromSerializable = {
                {
                    @Suppress("UNCHECKED_CAST")
                    it?.map { (key, value) ->
                        IndexedEnumComparable(
                            key,
                            value.first(),
                            value.subList(1, value.size).toSet()
                        )
                    }?.toTypedArray() as Array<IndexedEnum>
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
    }

    internal object Model :
        ContextualDataModel<IndexedEnumDefinition<IndexedEnum>, Properties, ContainsDefinitionsContext, EnumNameContext>(
            properties = Properties,
            contextTransformer = { EnumNameContext(it) }
        ) {
        override fun invoke(values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties>) =
            IndexedEnumDefinition<IndexedEnum>(
                name = values(1u),
                optionalCases = values(2u),
                reservedIndices = values(3u),
                reservedNames = values(4u),
                unknownCreator = { index, name -> IndexedEnumComparable(index, name) }
            )

        override fun writeJson(
            values: ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties>,
            writer: IsJsonLikeWriter,
            context: EnumNameContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(
            obj: IndexedEnumDefinition<IndexedEnum>,
            writer: IsJsonLikeWriter,
            context: EnumNameContext?
        ) {
            if (context?.definitionsContext?.enums?.containsKey(obj.name) == true) {
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
            context: EnumNameContext?
        ): ObjectValues<IndexedEnumDefinition<IndexedEnum>, Properties> {
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

        override fun readJsonToMap(reader: IsJsonLikeReader, context: EnumNameContext?) =
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
