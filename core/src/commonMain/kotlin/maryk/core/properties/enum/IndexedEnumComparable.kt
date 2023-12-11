package maryk.core.properties.enum

/** Comparable indexed enum */
interface IndexedEnumComparable<in E> : Comparable<E>, IndexedEnum {
    companion object {
        internal operator fun invoke(index: UInt, name: String, alternativeNames: Set<String>? = null) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index, alternativeNames) {
            override val name = name

            override fun equals(other: Any?) = other is IndexedEnum && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        }
    }
}
