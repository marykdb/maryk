package maryk.core.properties.enum

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsUsableInMultiType

/** Interface for Enums used in types which contain a strong type */
interface MultiTypeEnum<T: Any>: TypeEnum<T> {
    val definition: IsUsableInMultiType<T, *>?

    companion object {
        internal operator fun invoke(index: UInt, name: String, definition: IsUsableInMultiType<out Any, *>?, alternativeNames: Set<String>? = null) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index, alternativeNames), MultiTypeEnum<Any> {
            init {
                require(index > 0u) { "Only indices of 1 and higher are allowed" }
            }
            override val name = name
            @Suppress("UNCHECKED_CAST")
            override val definition: IsUsableInMultiType<Any, IsPropertyContext>? = definition as IsUsableInMultiType<Any, IsPropertyContext>

            override fun equals(other: Any?) = other is MultiTypeEnum<*> && other.index == this.index && other.definition == this.definition
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        } as MultiTypeEnum<Any>
    }
}
