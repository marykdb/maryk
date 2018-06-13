package maryk.core.properties.types

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.objects.AbstractDataModel
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
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
            add(0, "name", StringDefinition(), IndexedEnumDefinition<*>::name)
            add(1, "values",
                MapDefinition(
                    keyDefinition = NumberDefinition(
                        type = SInt32
                    ),
                    valueDefinition = StringDefinition()
                ),
                IndexedEnumDefinition<*>::values,
                toSerializable = {
                    it?.invoke()?.map { v: IndexedEnum<*> -> Pair(v.index, v.name) }?.toMap()
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
    internal object Model: AbstractDataModel<IndexedEnumDefinition<IndexedEnum<Any>>, Properties, DataModelContext, DataModelContext>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = IndexedEnumDefinition<IndexedEnum<Any>>(
            name = map(0),
            optionalValues = map(1)
        )
    }
}
