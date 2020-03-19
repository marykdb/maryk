package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import maryk.core.values.MutableValueItems
import maryk.core.values.Values

/** changes deeper value for [reference] based on [originalValue] and [newValue] with [valueChanger] */
internal fun deepValueChanger(originalValue: Any?, newValue: Any?, reference: AnyPropertyReference, valueChanger: (Any?, Any?) -> Any?) {
    when (newValue) {
        is Values<*, *> -> (newValue.values as MutableValueItems).copyFromOriginalAndChange(
            (originalValue as Values<*, *>).values,
            (reference as IsPropertyReferenceForValues<*, *, *, *>).index,
            valueChanger
        )
        is MutableList<*> -> when (reference) {
            is ListItemReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val changedValue = valueChanger(
                    (originalValue as List<Any>).getOrNull(reference.index.toInt()),
                    newValue[reference.index.toInt()]
                )

                if (changedValue != null) {
                    @Suppress("UNCHECKED_CAST")
                    (newValue as MutableList<Any>)[reference.index.toInt()] = changedValue
                }
            }
            is ListAnyItemReference<*, *> ->
                @Suppress("UNCHECKED_CAST")
                (newValue as MutableList<Any>).indices.forEach { index ->
                    @Suppress("UNCHECKED_CAST")
                    val changedValue = valueChanger(
                        (originalValue as List<Any>).getOrNull(index),
                        newValue[index]
                    )

                    if (changedValue != null) {
                        (newValue)[index] = changedValue
                    }
                }
            else -> throw RequestException("Unsupported reference type: $reference")
        }
        is MutableMap<*, *> -> when (reference) {
            is MapValueReference<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                val newMapValue = (newValue as MutableMap<Any, Any>)

                @Suppress("UNCHECKED_CAST")
                val changedValue = valueChanger(
                    (originalValue as Map<Any, Any>).getOrElse(reference.key) { null },
                    newMapValue
                )
                if (changedValue != null) {
                    newMapValue[reference.key] = changedValue
                }
            }
            is MapAnyValueReference<*, *, *> ->
                @Suppress("UNCHECKED_CAST")
                newValue.entries.forEach { (key, value) ->
                    @Suppress("UNCHECKED_CAST")
                    val newMapValue = (newValue as MutableMap<Any, Any>)

                    @Suppress("UNCHECKED_CAST")
                    val changedValue = valueChanger(
                        (originalValue as Map<Any, Any>).getOrElse(key as Any) { null },
                        value
                    )
                    if (changedValue != null) {
                        newMapValue[key] = changedValue
                    }
                }
            else -> throw RequestException("Unsupported reference type: $reference")
        }
        is TypedValue<*, *> -> when (reference) {
            is TypedValueReference<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                val changedValue = valueChanger(
                    (originalValue as TypedValue<*, *>).value,
                    newValue.value
                )
                if (changedValue != null) {
                    @Suppress("UNCHECKED_CAST")
                    (newValue as MutableTypedValue<TypeEnum<Any>, Any>).value = changedValue
                }
            }
            else -> throw RequestException("Unsupported reference type: $reference")
        }
        null -> throw RequestException("Cannot set sub value on non existing value")
        else -> throw RequestException("Unsupported reference type: $reference")
    }
}
