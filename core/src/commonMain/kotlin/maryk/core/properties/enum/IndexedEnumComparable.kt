package maryk.core.properties.enum

/** Comparable indexed enum */
interface IndexedEnumComparable<in E> : Comparable<E>, IndexedEnum {
    companion object {
        internal operator fun invoke(index: UInt, name: String) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index, name) {
            init {
                require(index > 0u) { "Only indices of 1 and higher are allowed" }
            }

            override val index = index
            override val name = name

            override fun equals(other: Any?) = other is IndexedEnum && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun toString() = name
        }
    }
}
