package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Definition for List properties with more complex values. Cannot itself not be transported. */
internal data class SubListDefinition<T : Any, CX : IsPropertyContext>(
    override val valueDefinition: IsSubDefinition<T, CX>
) : IsListDefinition<T, CX>,
    IsUsableInMapValue<List<T>, CX>,
    IsUsableInMultiType<List<T>, CX> {

    override val required: Boolean = true
    override val final: Boolean = false
    override val minSize: UInt? = null
    override val maxSize: UInt? = null
    override val default: List<T>? = null

    init {
        require(valueDefinition.required) { "Definition for value should have required=true on List" }
    }
}
