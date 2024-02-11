package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.TimePrecision

/** Time Property Definition to define time properties of [T] with precision. */
interface IsTimeDefinition<T : Comparable<T>> :
    IsComparableDefinition<T, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<T, IsPropertyContext> {
    val precision: TimePrecision

    override fun calculateStorageByteLength(value: T) = this.byteSize

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsComparableDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        (definition as? IsTimeDefinition<*>)?.let {
            if (definition.precision != this.precision) {
                addIncompatibilityReason?.invoke("The precision cannot be different: ${this.precision} vs ${definition.precision}")
                compatible = false
            }
        }

        return compatible
    }
}

open class TimePrecisionContext : IsPropertyContext {
    var precision: TimePrecision? = null
}
