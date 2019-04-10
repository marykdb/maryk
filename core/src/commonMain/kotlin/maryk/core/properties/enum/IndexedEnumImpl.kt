package maryk.core.properties.enum

import maryk.core.exceptions.DefNotFoundException

/**
 * Impl for Enums so they have indexes and can be transported and stored
 */
abstract class IndexedEnumImpl<E: IndexedEnum<E>>(
    override val index: UInt,
    name: String? = null
) : IndexedEnum<E> {
    override val name: String = name ?: this::class.simpleName ?: throw DefNotFoundException("Missing enum option name")

    override fun compareTo(other: E) = this.index.compareTo(other.index)
}
