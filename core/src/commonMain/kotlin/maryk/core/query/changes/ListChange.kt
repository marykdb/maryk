package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines changes to a list by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>) : this(listValueChange.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootModel>): ListChange? {
        val filtered = listValueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else ListChange(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (listValueChanges in listValueChanges) {
            listValueChanges.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef == null) {
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
    companion object : QueryModel<ListChange, Companion>() {
        val referenceListValueChangesPairs by list(
            index = 1u,
            getter = ListChange::listValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { ListValueChanges.Model }
            )
        )

        override fun invoke(values: ObjectValues<ListChange, Companion>): ListChange =
            Model.invoke(values)

        override val Model = object :
            ReferenceMappedDataModel<ListChange, ListValueChanges<*>, Companion, ListValueChanges.Companion>(
                properties = Companion,
                containedDataModel = ListValueChanges.Model,
                referenceProperty = ListValueChanges.reference
            ) {
            override fun invoke(values: ObjectValues<ListChange, Companion>) = ListChange(
                listValueChanges = values(1u)
            )

            override fun writeJson(obj: ListChange, writer: IsJsonLikeWriter, context: RequestContext?) {
                writeReferenceValueMap(writer, obj.listValueChanges, context)
            }
        }
    }
}
