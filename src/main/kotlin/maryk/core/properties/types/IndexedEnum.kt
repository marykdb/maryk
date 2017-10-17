package maryk.core.properties.types

interface IndexedEnum<E>: Comparable<E>{
    val name: String
    val index: Int
    val indexAsShortToStore: Short get() = (this.index + Short.MIN_VALUE).toShort()
}
