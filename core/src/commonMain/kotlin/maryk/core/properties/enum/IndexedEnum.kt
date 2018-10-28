package maryk.core.properties.enum

/**
 * Interface for Enums so they have indexes and can be transported and stored
 */
interface IndexedEnum<in E>: Comparable<E>{
    val index: Int
    val indexAsShortToStore: Short get() = (this.index + Short.MIN_VALUE).toShort()

    val name: String

    companion object {
        internal operator fun invoke(index: Int, name: String) = object :
            IndexedEnum<IndexedEnum<Any>> {
            override val index = index
            override val name = name

            override fun equals(other: Any?) = other is IndexedEnum<*> && other.index == this.index
            override fun hashCode() = index.hashCode()

            override fun compareTo(other: IndexedEnum<Any>) = this.index.compareTo(other.index)

            override fun toString() = name
        }
    }
}
