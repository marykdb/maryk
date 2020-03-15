package maryk.core.values

import maryk.core.exceptions.RequestException
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange

typealias ValuesImpl = Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM : IsValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM, P>() {
    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: P.() -> List<ValueItem>) =
        Values(
            dataModel,
            values.copyAdding(pairCreator(this.dataModel.properties)),
            context
        )

    fun copy(values: IsValueItems) =
        Values(dataModel, values.copyAdding(values), context)

    fun filterWithSelect(select: IsPropRefGraph<*>?): Values<DM, P> {
        if (select == null) {
            return this
        }

        return Values(
            dataModel = dataModel,
            values = this.values.copySelecting(select),
            context = context
        )
    }

    /** Change the Values with given [change] */
    fun change(vararg change: IsChange) = this.change(listOf(*change))

    internal fun change(changes: List<IsChange>): Values<DM, P> =
        if (changes.isEmpty()) {
            this
        } else {
            val mutableReferenceList = mutableListOf<AnyPropertyReference>()

            val valueItemsToChange = MutableValueItems(mutableListOf())

            for (change in changes) {
                when (change) {
                    is Change -> {
                        for (referenceValuePair in change.referenceValuePairs) {
                            referenceValuePair.reference.unwrap(mutableReferenceList)

                            var referenceIndex = 0

                            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                                return if (currentRef == null) {
                                    referenceValuePair.value
                                } else {
                                    when (newValue) {
                                        is Values<*, *> -> (newValue.values as MutableValueItems).copyFromOriginalAndChange(
                                            (originalValue as Values<*, *>).values,
                                            (currentRef as IsPropertyReferenceForValues<*, *, *, *>).index,
                                            ::valueChanger
                                        )
                                        is MutableList<*> -> when (currentRef) {
                                            is ListItemReference<*, *> -> {
                                                @Suppress("UNCHECKED_CAST")
                                                (newValue as MutableList<Any>)[currentRef.index.toInt()] = referenceValuePair.value
                                            }
                                            is ListAnyItemReference<*, *> ->
                                                @Suppress("UNCHECKED_CAST")
                                                (newValue as MutableList<Any>).indices.forEach { index ->
                                                    newValue[index] = referenceValuePair.value
                                                }
                                            else -> throw RequestException("Unsupported reference type: $currentRef")
                                        }
                                        is MutableMap<*, *> -> when (currentRef) {
                                            is MapValueReference<*, *, *> -> {
                                                @Suppress("UNCHECKED_CAST")
                                                (newValue as MutableMap<Any, Any>)[currentRef.key] = referenceValuePair.value
                                            }
                                            is MapAnyValueReference<*, *, *> ->
                                                @Suppress("UNCHECKED_CAST")
                                                newValue.entries.forEach { (key, _) ->
                                                    (newValue as MutableMap<Any, Any>)[key as Any] = referenceValuePair.value
                                                }
                                            else -> throw RequestException("Unsupported reference type: $currentRef")
                                        }
                                        is TypedValue<*, *> -> when (currentRef) {
                                            is TypedValueReference<*, *, *> ->
                                                @Suppress("UNCHECKED_CAST")
                                                (newValue as MutableTypedValue<TypeEnum<Any>, Any>).value = referenceValuePair.value
                                            else -> throw RequestException("Unsupported reference type: $currentRef")
                                        }
                                        null -> throw RequestException("Cannot set sub value on non existing value")
                                        else -> throw RequestException("Unsupported reference type: $currentRef")
                                    }
                                    null // Deeper change so no overwrite
                                }
                            }

                            when (val ref = mutableReferenceList[referenceIndex++]) {
                                is IsPropertyReferenceForValues<*, *, *, *> ->
                                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index, ::valueChanger)
                                else -> throw RequestException("Unsupported reference type: $ref")
                            }
                        }
                    }
                    is Delete -> TODO()
                    is ListChange -> {
                        for (listValueChanges in change.listValueChanges) {
                            listValueChanges.reference.unwrap(mutableReferenceList)
                            var referenceIndex = 0

                            when (val ref = mutableReferenceList[referenceIndex++]) {
                                is IsPropertyReferenceForValues<*, *, *, *> ->
                                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index) { _: Any?, newValue: Any? ->
                                        when (val currentRef = mutableReferenceList.getOrNull(referenceIndex++)) {
                                            null -> {
                                                when (newValue) {
                                                    is MutableList<*> -> {
                                                        listValueChanges.deleteValues?.let { newValue.removeAll(it) }
                                                        listValueChanges.addValuesAtIndex?.let {
                                                            for ((index, value) in it) {
                                                                @Suppress("UNCHECKED_CAST")
                                                                (newValue as MutableList<Any>).add(index.toInt(), value)
                                                            }
                                                        }
                                                        listValueChanges.addValuesToEnd?.let {
                                                            @Suppress("UNCHECKED_CAST")
                                                            (newValue as MutableList<Any>).addAll(it)
                                                        }
                                                    }
                                                    null -> throw RequestException("Cannot set list changes on non existing value")
                                                    else -> throw RequestException("Unsupported value type: $newValue for ref: $currentRef")
                                                }
                                                null
                                            }
                                            else -> throw RequestException("Cannot change lists which are not the last reference")
                                        }
                                    }
                                else -> throw RequestException("Unsupported reference type: $ref")
                            }
                        }
                    }
                    is SetChange -> {
                        for (setValueChanges in change.setValueChanges) {
                            setValueChanges.reference.unwrap(mutableReferenceList)
                            var referenceIndex = 0

                            when (val ref = mutableReferenceList[referenceIndex++]) {
                                is IsPropertyReferenceForValues<*, *, *, *> ->
                                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index) { _: Any?, newValue: Any? ->
                                        when (val currentRef = mutableReferenceList.getOrNull(referenceIndex++)) {
                                            null -> {
                                                when (newValue) {
                                                    is MutableSet<*> -> {
                                                        setValueChanges.addValues?.let {
                                                            @Suppress("UNCHECKED_CAST")
                                                            (newValue as MutableSet<Any>).addAll(it)
                                                        }
                                                    }
                                                    null -> throw RequestException("Cannot set set changes on non existing value")
                                                    else -> throw RequestException("Unsupported value type: $newValue for ref: $currentRef")
                                                }
                                                null
                                            }
                                            else -> throw RequestException("Cannot change sets which are not the last reference")
                                        }
                                    }
                                else -> throw RequestException("Unsupported reference type: $ref")
                            }
                        }
                    }
                    is IncMapChange -> TODO()
                    is IncMapAddition -> TODO()
                    else -> throw Exception("Unexpected Change to process: $change")
                }
            }

            Values(dataModel, values.copyAdding(valueItemsToChange.list), context)
        }

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Values<*, *> -> false
        dataModel != other.dataModel -> false
        values != other.values -> false
        else -> true
    }

    // ignore context
    override fun hashCode(): Int {
        var result = dataModel.hashCode()
        result = 31 * result + values.hashCode()
        return result
    }

    override fun toString(): String {
        val modelName = (dataModel as? IsNamedDataModel<*>)?.name ?: dataModel
        return "Values<$modelName>$values"
    }

    /**
     * Validates the contents of values
     */
    fun validate() {
        @Suppress("UNCHECKED_CAST")
        (this.dataModel as IsTypedValuesDataModel<DM, P>).validate(this)
    }
}
