package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.models.ContextualDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Enum Definitions with a [name] and [values] */
open class IndexedEnumDefinition<E: IndexedEnum<E>> private constructor(
    internal val optionalValues: (() -> Array<E>)?,
    override val name: String
): MarykPrimitive {
    constructor(name: String, values: () -> Array<E>) : this(name = name, optionalValues = values)

    val values get() = optionalValues!!

    override val primitiveType = PrimitiveType.EnumDefinition

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
        val values = add(2, "values",
            MapDefinition(
                keyDefinition = NumberDefinition(
                    type = SInt32
                ),
                valueDefinition = StringDefinition()
            ) as MapDefinition<Int, String, EnumNameContext>,
            IndexedEnumDefinition<*>::values,
            toSerializable = { value, context ->
                // If Enum was defined before and is thus available in context, don't include the values again
                val toReturnNull = context?.let { enumNameContext ->
                    if (enumNameContext.isOriginalDefinition == true) {
                        false
                    } else {
                        enumNameContext.definitionsContext?.let {
                            if(it.enums[enumNameContext.name] == null) {
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
    internal object Model: ContextualDataModel<IndexedEnumDefinition<IndexedEnum<Any>>, Properties, ContainsDefinitionsContext, EnumNameContext>(
        properties = Properties,
        contextTransformer = { EnumNameContext(it) }
    ) {
        override fun invoke(values: ObjectValues<IndexedEnumDefinition<IndexedEnum<Any>>, Properties>) =
            IndexedEnumDefinition<IndexedEnum<Any>>(
                name = values(1),
                optionalValues = values(2)
            )

        override fun writeJson(obj: IndexedEnumDefinition<IndexedEnum<Any>>, writer: IsJsonLikeWriter, context: EnumNameContext?) {
            if (context?.definitionsContext?.enums?.containsKey(obj.name) == true) {
                // Write a single string name if no options was defined
                val value = Properties.name.getPropertyAndSerialize(obj, context)
                        ?: throw ParseException("Missing requests in Requests")
                Properties.name.writeJsonValue(value, writer, context)
                Properties.name.capture(context, value)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: EnumNameContext?): ObjectValues<IndexedEnumDefinition<IndexedEnum<Any>>, Properties>{
            if (reader.currentToken == JsonToken.StartDocument){
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
    }
}
