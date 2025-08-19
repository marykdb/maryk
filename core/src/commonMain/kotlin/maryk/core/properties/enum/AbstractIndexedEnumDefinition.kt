package maryk.core.properties.enum

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WireType.VAR_INT
import maryk.lib.exceptions.ParseException

abstract class AbstractIndexedEnumDefinition<E: IndexedEnum>(
    internal val optionalCases: (() -> List<E>)?,
    final override val name: String,
    final override val reservedIndices: List<UInt>? = null,
    final override val reservedNames: List<String>? = null,
    private val unknownCreator: ((UInt, String) -> E)? = null
): IsIndexedEnumDefinition<E> {
    override val wireType: WireType = VAR_INT

    final override val byteSize = 2
    final override val required = true
    final override val final = true

    // Because of compilation issue in Native this map contains IndexedEnum<E> instead of E as value
    private val valueByString: Map<String, E> by lazy<Map<String, E>> {
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

    private val valueByIndex by lazy {
        cases().associateBy { it.index }
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

                typeByName ?: unknownCreator?.invoke(index, valueName)
            } catch (_: NumberFormatException) {
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
                other.optionalCases.invoke() == optionalCases.invoke()
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

    /**
     * Checks specific enum types
     * Override to handle specific enum types
     */
    internal open fun enumTypeIsCompatible(
        storedEnum: E,
        newEnum: E,
        checkedDataModelNames: MutableList<String>? = null,
        addIncompatibilityReason: ((String) -> Unit)?
    ) = true

    final override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?,
    ): Boolean {
        var compatible = super.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        if (definition is AbstractIndexedEnumDefinition) {
            if (name != definition.name) {
                addIncompatibilityReason?.invoke("Enum name $name is not the same as ${definition.name}")
                compatible = false
            }

            if (definition.reservedNames != null && reservedNames?.containsAll(definition.reservedNames) != true) {
                addIncompatibilityReason?.invoke("Enum does not contain all previous reserved names: $reservedNames")
                compatible = false
            }

            if (definition.reservedIndices != null && reservedIndices?.containsAll(definition.reservedIndices) != true) {
                addIncompatibilityReason?.invoke("Enum does not contain all previous reserved names: $reservedIndices")
                compatible = false
            }

            val newIterator = this.cases().iterator()
            val storedIterator = definition.cases().iterator()

            var newProperty: E? = newIterator.next()
            var storedProperty: IndexedEnum? = storedIterator.next()

            /** Process new enum not present on stored model. */
            fun processNew() {
                newProperty = if (newIterator.hasNext()) newIterator.next() else null
            }

            /**
             * Stored enum was not present in new data model so it should have been added to reserved
             * indexes and names. Otherwise, should be handled by a migration.
             */
            fun processStored(storedEnum: IndexedEnum) {
                if (this.reservedIndices?.contains(storedEnum.index) != true) {
                    addIncompatibilityReason?.invoke("Enum with index ${storedEnum.index} is not present in new model. Please add it to `reservedIndices` or add it back to avoid this exception.")
                    compatible = false
                }
                val allNames = storedEnum.alternativeNames?.let { it + storedEnum.name } ?: setOf(storedEnum.name)
                if (this.reservedNames?.containsAll(allNames) != true) {
                    addIncompatibilityReason?.invoke("Enum with name(s) `${allNames.joinToString()}` is not present in new model. Please add it to `reservedNames` or add it back to avoid this exception.")
                    compatible = false
                }
                storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
            }

            /**
             * Compare stored with new properties. If incompatible changes are encountered a migration should be done
             */
            fun compareNewWithStored(storedEnum: IndexedEnum, newEnum: E) {
                if (storedEnum.name != newEnum.name && newEnum.alternativeNames?.contains(storedEnum.name) != true) {
                    addIncompatibilityReason?.invoke("Name `${storedEnum.name}` is not present in new enum for ${storedEnum.index}. Please add it to `reservedNames`.")
                    compatible = false
                }

                @Suppress("UNCHECKED_CAST")
                if (!enumTypeIsCompatible(storedEnum as E, newEnum, checkedDataModelNames, addIncompatibilityReason)) {
                    compatible = false
                }

                storedProperty = if (storedIterator.hasNext()) storedIterator.next() else null
                newProperty = if (newIterator.hasNext()) newIterator.next() else null
            }

            while (newProperty != null || storedProperty != null) {
                val newProp = newProperty
                val storedProp = storedProperty
                when {
                    newProp == null ->
                        processStored(storedProp!!)
                    storedProp == null ->
                        processNew()
                    newProp.index == storedProp.index ->
                        compareNewWithStored(storedProp, newProp)
                    newProp.index < storedProp.index ->
                        if (newIterator.hasNext()) processNew() else processStored(storedProp)
                    newProp.index > storedProp.index ->
                        if (storedIterator.hasNext()) processStored(storedProp) else processNew()
                }
            }
        }

        return compatible
    }
}
