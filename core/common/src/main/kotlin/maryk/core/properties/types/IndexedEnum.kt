package maryk.core.properties.types

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.types.numeric.SInt32
import maryk.core.query.DataModelContext

interface IndexedEnum<in E>: Comparable<E>{
    val index: Int
    val indexAsShortToStore: Short get() = (this.index + Short.MIN_VALUE).toShort()

    val name: String

    companion object {
        internal operator fun invoke(index: Int, name: String) = object : IndexedEnum<IndexedEnum<Any>>{
            override val index = index
            override val name = name

            override fun equals(other: Any?) = other is IndexedEnum<*> && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun compareTo(other: IndexedEnum<Any>) = this.index.compareTo(other.index)

            override fun toString() = name
        }
    }
}

/** Enum Definitions with a [name] and [values] */
open class IndexedEnumDefinition<E: IndexedEnum<E>> private constructor(
    internal val optionalValues: (() -> Array<E>)?,
    override val name: String
): MarykPrimitive {
    constructor(name: String, values: () -> Array<E>) : this(name = name, optionalValues = values)

    val values get() = optionalValues!!

    override val primitiveType = PrimitiveType.EnumDefinition

    internal object Properties : PropertyDefinitions<IndexedEnumDefinition<IndexedEnum<Any>>>() {
        init {
            add(0, "name",
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
            add(1, "values",
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
                            enumNameContext.dataModelContext?.let {
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
                        it?.map { IndexedEnum(it.key, it.value) }?.toTypedArray() as Array<IndexedEnum<*>>
                    }
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal object Model: ContextualDataModel<IndexedEnumDefinition<IndexedEnum<Any>>, Properties, DataModelContext, EnumNameContext>(
        properties = Properties,
        contextTransformer = { EnumNameContext(it) }
    ) {
        override fun invoke(map: Map<Int, *>) = IndexedEnumDefinition<IndexedEnum<Any>>(
            name = map(0),
            optionalValues = map(1)
        )
    }
}

class EnumNameContext(
    val dataModelContext: DataModelContext? = null
): IsPropertyContext {
    var name: String? = null
    var isOriginalDefinition: Boolean? = false
}
