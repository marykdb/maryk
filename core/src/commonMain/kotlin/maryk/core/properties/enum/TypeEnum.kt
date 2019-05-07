package maryk.core.properties.enum

/** Interface for Enums used in types which contain a strong type */
@Suppress("unused")
interface TypeEnum<out T: Any>: IndexedEnum {
    companion object {
        internal operator fun invoke(index: UInt, name: String) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index), TypeEnum<Any> {
            init {
                require(index > 0u) { "Only indices of 1 and higher are allowed" }
            }

            override val name = name

            override fun equals(other: Any?) = other is TypeEnum<Any> && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        } as TypeEnum<Any>
    }
}
