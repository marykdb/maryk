package maryk.core.properties.enum

import maryk.lib.exceptions.ParseException
import maryk.lib.safeLazy

abstract class AbstractIndexedEnumDefinition<E: IndexedEnum>(
    internal val optionalCases: (() -> Array<E>)?,
    final override val name: String,
    final override val reservedIndices: List<UInt>? = null,
    final override val reservedNames: List<String>? = null,
    private val unknownCreator: ((UInt, String) -> E)? = null
): IsIndexedEnumDefinition<E> {
    final override val byteSize = 2
    final override val required = true
    final override val final = true

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByString: Map<String, E> by safeLazy<Map<String, E>> {
        mutableMapOf<String, E>().also { output ->
            for (type in cases()) {
                output[type.name] = type
                type.alternativeNames?.forEach { name: String ->
                    if (output.containsKey(name)) throw ParseException("Enum ${this@AbstractIndexedEnumDefinition.name} already has a case for $name")
                    output[name] = type
                }
            }
        }
    }

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByIndex by safeLazy {
        cases().associate { Pair(it.index, it) }
    }

    override val cases get() = optionalCases!!

    override fun resolve(index: UInt) = valueByIndex[index] ?: unknownCreator?.invoke(index, "%Unknown")

    /** Get Enum value by [name] */
    override fun resolve(name: String): E? =
        if (name.endsWith(')')) {
            val found = name.split('(', ')')
            try {
                val index = found[1].toUInt()
                val valueName = found[0]

                val typeByName = valueByString[valueName]
                if (typeByName != null && typeByName.index != index) {
                    throw ParseException("Non matching name $valueName with index $index, expected ${typeByName.index}")
                }

                valueByIndex[index] ?: unknownCreator?.invoke(index, valueName)
            } catch (e: NumberFormatException) {
                throw ParseException("Not a correct number between brackets in type $name")
            }
        } else {
            valueByString[name] ?: unknownCreator?.invoke(0u, name)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IndexedEnumDefinition<*>) return false

        if (optionalCases != null) {
            return if (other.optionalCases != null) {
                other.optionalCases.invoke().contentEquals(optionalCases.invoke())
            } else false
        }
        if (name != other.name) return false
        if (reservedIndices != other.reservedIndices) return false
        if (reservedNames != other.reservedNames) return false

        return true
    }

    override fun hashCode(): Int {
        var result = optionalCases?.invoke().hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + reservedIndices.hashCode()
        result = 31 * result + reservedNames.hashCode()
        return result
    }
}
