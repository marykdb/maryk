package maryk.core.properties.enum

/** Interface for Enums used in types which contain a strong type */
interface MultiTypeEnum<out T: Any>: TypeEnum<T> {
    companion object {
        internal operator fun invoke(index: UInt, name: String, alternativeNames: Set<String>? = null) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index, alternativeNames), MultiTypeEnum<Any> {
            init {
                require(index > 0u) { "Only indices of 1 and higher are allowed" }
            }

            override val name = name

            override fun equals(other: Any?) = other is MultiTypeEnum<Any> && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        } as MultiTypeEnum<Any>
    }
}
