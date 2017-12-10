package maryk.core.properties.types

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.numeric.UInt16
import maryk.core.properties.types.numeric.toUInt16

interface IndexedEnum<in E>: Comparable<E>{
    val index: Int
    val indexAsShortToStore: Short get() = (this.index + Short.MIN_VALUE).toShort()

    val name: String

    companion object : DataModel<IndexedEnum<*>, PropertyDefinitions<IndexedEnum<*>>, IsPropertyContext>(
            properties = object : PropertyDefinitions<IndexedEnum<*>>() {
                init {
                    add(0, "index", NumberDefinition(type = UInt16)) {
                        it.index.toUInt16()
                    }
                    add(1, "name", StringDefinition(), IndexedEnum<*>::name)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = this(
                index = (map[0] as UInt16).toInt(),
                name = (map[1] as String)
        )

        operator fun invoke(index: Int, name: String) = object : IndexedEnum<IndexedEnum<Any>>{
            override val index = index
            override val name = name

            override fun compareTo(other: IndexedEnum<Any>) = this.index.compareTo(other.index)

            override fun toString() = name
        }
    }
}
