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

open class IndexedEnumDefinition<E: IndexedEnum<E>>(
    override val name: String,
    val values: () -> Array<E>
): MarykPrimitive {
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
            values = map(1)
        )
    }
}
