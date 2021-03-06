package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines change to sets by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>) : this(setValueChange.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): SetChange? {
        val filtered = setValueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else SetChange(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (setValueChanges in setValueChanges) {
            setValueChanges.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef == null) {
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
                } else {
                    deepValueChanger(
                        originalValue,
                        newValue,
                        currentRef,
                        ::valueChanger
                    )
                    null // Deeper change so no overwrite
                }
            }

            when (val ref = mutableReferenceList[referenceIndex++]) {
                is IsPropertyReferenceForValues<*, *, *, *> -> objectChanger(ref, ::valueChanger)
                else -> throw RequestException("Unsupported reference type: $ref")
            }
        }
    }

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<SetChange>() {
        val setValueChanges by list(
            index = 1u,
            getter = SetChange::setValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { SetValueChanges }
            )
        )
    }

    companion object : ReferenceMappedDataModel<SetChange, SetValueChanges<*>, Properties, SetValueChanges.Properties>(
        properties = Properties,
        containedDataModel = SetValueChanges,
        referenceProperty = SetValueChanges.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<SetChange, Properties>) = SetChange(
            setValueChanges = values(1u)
        )

        override fun writeJson(obj: SetChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.setValueChanges, context)
        }
    }
}
