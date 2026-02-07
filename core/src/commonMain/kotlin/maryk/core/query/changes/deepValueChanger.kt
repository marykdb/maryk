package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import maryk.core.values.MutableValueItems
import maryk.core.values.Values

// Thrown when sub object was tried to be changed.
object SubObjectChangeException: RequestException("Cannot set sub value on non existing value")

/** changes deeper value for [reference] based on [originalValue] and [newValue] with [valueChanger] */
internal fun deepValueChanger(originalValue: Any?, newValue: Any?, reference: AnyPropertyReference, valueChanger: (Any?, Any?) -> Any?) {
    when (newValue) {
        is Values<*> -> (newValue.values as MutableValueItems).copyFromOriginalAndChange(
            (originalValue as Values<*>).values,
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

                @Suppress("UNCHECKED_CAST")
                when(changedValue) {
                    Unit -> newValue.removeAt(reference.index.toInt())
                    null -> {} // Do nothing
                    else -> (newValue as MutableList<Any>)[reference.index.toInt()] = changedValue
                }
            }
            is ListAnyItemReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                for (index in (originalValue as List<Any>).lastIndex downTo 0) {
                    val changedValue = valueChanger(
                        originalValue.getOrNull(index),
                        (newValue as MutableList<Any>)[index]
                    )

                    when(changedValue) {
                        Unit -> newValue.removeAt(index)
                        null -> {} // Do nothing
                        else -> newValue[index] = changedValue
                    }
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
                    newMapValue[reference.key]
                )
                when(changedValue) {
                    Unit -> newMapValue.remove(reference.key)
                    null -> {} // Do nothing
                    else -> newMapValue[reference.key] = changedValue
                }
            }
            is MapAnyValueReference<*, *, *> -> {
                var itemsToRemove: MutableList<Any>? = null
                @Suppress("UNCHECKED_CAST")
                val newMapValue = (newValue as MutableMap<Any, Any>)

                for ((key, value) in newMapValue.entries) {
                    @Suppress("UNCHECKED_CAST")
                    val changedValue = valueChanger(
                        (originalValue as Map<Any, Any>).getOrElse(key) { null },
                        value
                    )
                    when(changedValue) {
                        Unit -> {
                            itemsToRemove = itemsToRemove?.apply { add(key) } ?: mutableListOf(key)
                        }
                        null -> {} // Do nothing
                        else -> newMapValue[key] = changedValue
                    }
                }
                itemsToRemove?.forEach {
                    newMapValue.remove(it)
                }
            }
            else -> throw RequestException("Unsupported reference type: $reference")
        }
        is MutableSet<*> -> when (reference) {
            is SetItemReference<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val newSetValue = newValue as MutableSet<Any>

                @Suppress("UNCHECKED_CAST")
                val currentValue = (originalValue as? Set<Any>)?.let {
                    if (it.contains(reference.value)) reference.value else null
                }
                val newCurrentValue = if (newSetValue.contains(reference.value)) reference.value else null

                @Suppress("UNCHECKED_CAST")
                val changedValue = valueChanger(
                    currentValue,
                    newCurrentValue
                )
                when (changedValue) {
                    Unit -> newSetValue.remove(reference.value)
                    null -> {} // Do nothing
                    else -> newSetValue.add(changedValue)
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
        null -> throw SubObjectChangeException
        else -> throw RequestException("Unsupported reference type: $reference")
    }
}
