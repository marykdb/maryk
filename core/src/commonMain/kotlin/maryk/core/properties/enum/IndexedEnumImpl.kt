package maryk.core.properties.enum

import maryk.core.exceptions.DefNotFoundException

/**
 * Impl for Enums so they have indexes and can be transported and stored
 */
abstract class IndexedEnumImpl<E: IndexedEnum>(
    override val index: UInt
) : IndexedEnumComparable<E> {
    override val name get() = this::class.simpleName ?: throw DefNotFoundException("Missing enum option name")

    override fun compareTo(other: E) = this.index.compareTo(other.index)

    override fun equals(other: Any?) = other is IndexedEnum && index == other.index && name == other.name

    override fun hashCode() = 31 * index.hashCode() + name.hashCode()

    override fun toString() = name
}
