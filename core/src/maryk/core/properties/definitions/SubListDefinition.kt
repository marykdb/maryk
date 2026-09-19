package maryk.core.properties.definitions

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
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
}

fun <T: Any, TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.subList(
    index: UInt,
    getter: (DO) -> List<TO>?,
    name: String? = null,
    valueDefinition: IsSubDefinition<T, CX>,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO) -> T)? = null,
    fromSerializable: ((T) -> TO)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, List<T>) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    ListDefinitionWrapper(
        index,
        name ?: propName,
        SubListDefinition(valueDefinition),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable?.let { toSerializable ->
            val toSerializableList: (List<TO>?, CX?) -> List<T>? = { value: List<TO>?, _: CX? ->
                value?.map { toSerializable(it) }
            }
            toSerializableList
        },
        fromSerializable = fromSerializable?.let { fromSerializable ->
            val fromSerializableList: (List<T>?) -> List<TO>? = { value: List<T>? ->
                value?.map { fromSerializable(it) }
            }
            fromSerializableList
        },
        shouldSerialize = shouldSerialize
    )
}
