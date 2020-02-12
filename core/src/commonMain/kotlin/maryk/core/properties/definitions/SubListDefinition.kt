package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader

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

fun <T: Any, TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.subList(
    index: UInt,
    getter: (DO) -> List<TO>?,
    name: String? = null,
    valueDefinition: IsSubDefinition<T, CX>,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO) -> T)? = null,
    fromSerializable: (Unit.(T) -> TO)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, List<T>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    @Suppress("UNCHECKED_CAST")
    (ListDefinitionWrapper(
        index,
        name ?: propName,
        SubListDefinition(valueDefinition),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable?.let { toSerializable ->
            val toSerializableList: Unit.(List<TO>?, CX?) -> List<T>? = { value: List<TO>?, _: CX? ->
                value?.map { toSerializable(Unit, it) }
            }
            toSerializableList
        },
        fromSerializable = fromSerializable?.let { fromSerializable ->
            val fromSerializableList: Unit.(List<T>?) -> List<TO>? = { value: List<T>? ->
                value?.map { fromSerializable(Unit, it) }
            }
            fromSerializableList
        },
        shouldSerialize = shouldSerialize
    ))
}
