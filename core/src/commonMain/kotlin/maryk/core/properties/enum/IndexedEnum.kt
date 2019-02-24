package maryk.core.properties.enum

typealias AnyIndexedEnum = IndexedEnum<IndexedEnum<IndexedEnum<Any>>>

/**
 * Interface for Enums so they have indexes and can be transported and stored
 */
interface IndexedEnum<in E> : Comparable<E> {
    val index: UInt
    val name: String

    companion object {
        internal operator fun invoke(index: UInt, name: String) = object :
            AnyIndexedEnum {
            override val index = index
            override val name = name

            override fun equals(other: Any?) = other is IndexedEnum<*> && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun compareTo(other: IndexedEnum<IndexedEnum<Any>>) = this.index.compareTo(other.index)

            override fun toString() = name
        }
    }
}
