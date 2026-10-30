package maryk.core.values

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.Check
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IncMapKeyAdditions
import maryk.core.query.changes.IncMapValueChanges
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.ListValueChanges
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.SetValueChanges
import maryk.core.query.pairs.ReferenceValuePair

/** Copy built-in value containers and typed wrappers so their caller-owned operands can be reused. */
fun <DM : IsValuesDataModel> Values<DM>.copyWithOwnedValues(): Values<DM> =
    Values(dataModel, MutableValueItems(mapTo(ArrayList(size)) { (index, value) ->
        ValueItem(index, copyValueWithOwnedContent(value))
    }), context)

@Suppress("UNCHECKED_CAST")
internal fun copyValueWithOwnedContent(value: Any): Any = when (value) {
    is Values<*> -> value.copyWithOwnedValues()
    is MutableTypedValue<*, *> -> MutableTypedValue(value.type, copyValueWithOwnedContent(value.value))
    is TypedValue<*, *> -> value.type(copyValueWithOwnedContent(value.value))
    is List<*> -> value.map { it?.let(::copyValueWithOwnedContent) }
    is Set<*> -> value.mapTo(linkedSetOf()) { it?.let(::copyValueWithOwnedContent) }
    is Map<*, *> -> value.entries.associate { (key, item) -> key to item?.let(::copyValueWithOwnedContent) }
    is ByteArray -> value.copyOf()
    else -> value
}

@Suppress("UNCHECKED_CAST")
/** Copy built-in mutation operands before retaining them beyond the lifetime of a request. */
fun IsChange.copyWithOwnedValues(): IsChange = when (this) {
    is Change -> copy(referenceValuePairs = referenceValuePairs.map { pair ->
        if (pair is ReferenceValuePair<Any>) pair.copy(value = copyValueWithOwnedContent(pair.value)) else pair
    })
    is Check -> copy(referenceValuePairs = referenceValuePairs.map { it.copy(value = copyValueWithOwnedContent(it.value)) })
    is ListChange -> copy(listValueChanges = listValueChanges.map { item ->
        (item as ListValueChanges<Any>).copy(
            deleteValues = item.deleteValues?.map(::copyValueWithOwnedContent),
            addValuesAtIndex = item.addValuesAtIndex?.mapValues { copyValueWithOwnedContent(it.value) },
            addValuesToEnd = item.addValuesToEnd?.map(::copyValueWithOwnedContent),
        )
    })
    is SetChange -> copy(setValueChanges = setValueChanges.map { item ->
        (item as SetValueChanges<Any>).copy(addValues = item.addValues?.mapTo(linkedSetOf(), ::copyValueWithOwnedContent))
    })
    is IncMapChange -> copy(valueChanges = valueChanges.map { item ->
        (item as IncMapValueChanges<Comparable<Any>, Any>).copy(addValues = item.addValues?.map(::copyValueWithOwnedContent))
    })
    is IncMapAddition -> copy(additions = additions.map { item ->
        (item as IncMapKeyAdditions<Comparable<Any>, Any>).copy(
            addedKeys = item.addedKeys?.toList(), addedValues = item.addedValues?.map(::copyValueWithOwnedContent),
        )
    })
    is MultiTypeChange -> copy(referenceTypePairs = referenceTypePairs.toList())
    is IndexChange -> copy(changes = changes.toList())
    else -> this
}
