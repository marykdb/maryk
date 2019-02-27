package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Enum Definitions with a [name] and [cases] */
open class IndexedEnumDefinition<E : IndexedEnum<E>> private constructor(
    internal val optionalCases: (() -> Array<E>)?,
    override val name: String
) : MarykPrimitive,
    IsPropertyDefinition<E>,
    IsFixedBytesEncodable<E> {

    override val primitiveType = PrimitiveType.EnumDefinition

    override val byteSize = 2
    override val required = true
    override val final = true

    val valueByString: Map<String, E> by lazy {
        cases().associate { Pair(it.name, it) }
    }
    val valueByIndex: Map<UInt, E> by lazy {
        cases().associate { Pair(it.index, it) }
    }

    val cases get() = optionalCases!!

    constructor(name: String, values: () -> Array<E>) : this(name = name, optionalCases = values)

    override fun getEmbeddedByName(name: String): Nothing? = null
    override fun getEmbeddedByIndex(index: Int): Nothing? = null

    override fun readStorageBytes(length: Int, reader: () -> Byte): E {
        val index = initUInt(reader, 2)
        return valueByIndex[index]
            ?: throw DefNotFoundException("Unknown index $index for $name")
    }

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.index.writeBytes(writer, 2)
    }

    internal object Properties : ObjectPropertyDefinitions<IndexedEnumDefinition<IndexedEnum<Any>>>() {
        val name = add(1, "name",
            ContextCaptureDefinition(
                definition = StringDefinition(),
                capturer = { context: EnumNameContext?, value ->
                    context?.let {
                        it.name = value
                    }
                }
            ),
            IndexedEnumDefinition<*>::name
        )

        @Suppress("UNCHECKED_CAST")
        val cases = add(2, "cases",
            MapDefinition(
                keyDefinition = NumberDefinition(
                    type = UInt32
                ),
                valueDefinition = StringDefinition()
            ) as MapDefinition<UInt, String, EnumNameContext>,
            IndexedEnumDefinition<*>::cases,
            toSerializable = { value, context ->
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
                    value?.invoke()?.map { v: IndexedEnum<*> -> Pair(v.index, v.name) }?.toMap()
                }
            },
            fromSerializable = {
                {
                    @Suppress("UNCHECKED_CAST")
                    it?.map { entry ->
                        IndexedEnum(
                            entry.key,
                            entry.value
                        )
                    }?.toTypedArray() as Array<IndexedEnum<*>>
                }
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal object Model :
        ContextualDataModel<IndexedEnumDefinition<IndexedEnum<Any>>, Properties, ContainsDefinitionsContext, EnumNameContext>(
            properties = Properties,
            contextTransformer = { EnumNameContext(it) }
        ) {
        override fun invoke(values: ObjectValues<IndexedEnumDefinition<IndexedEnum<Any>>, Properties>) =
            IndexedEnumDefinition<IndexedEnum<Any>>(
                name = values(1),
                optionalCases = values(2)
            )

        override fun writeJson(
            values: ObjectValues<IndexedEnumDefinition<IndexedEnum<Any>>, Properties>,
            writer: IsJsonLikeWriter,
            context: EnumNameContext?
        ) {
            throw SerializationException("Cannot write definitions from Values")
        }

        override fun writeJson(
            obj: IndexedEnumDefinition<IndexedEnum<Any>>,
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
                        // Write only cases if it is inside Definitions
                        Properties.cases.writeJsonValue(
                            Properties.cases.toSerializable!!.invoke(obj.cases, context)!!,
                            writer,
                            context
                        )
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
        ): ObjectValues<IndexedEnumDefinition<IndexedEnum<Any>>, Properties> {
            if (reader.currentToken == JsonToken.StartDocument) {
                reader.nextToken()
            }

            return if (reader.currentToken is JsonToken.Value<*>) {
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
                        // If a name was defined, read map as values
                        MutableValueItems().also {
                            it[Properties.name.index] = name
                            it[Properties.cases.index] = Properties.cases.readJson(reader, context)
                        }
                    }
                }
            }
    }
}
