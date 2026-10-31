package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.models.serializers.ReferenceMappedDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.references.toListIndex
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.mutableValueCreator
import maryk.json.IsJsonLikeWriter

/** Defines changes to a list by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>) : this(listValueChange.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): ListChange? {
        val filtered = listValueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else ListChange(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        listValueChanges.forEach { valueChange ->
            @Suppress("UNCHECKED_CAST")
            val listDefinition = valueChange.reference.comparablePropertyDefinition as IsListDefinition<Any, IsPropertyContext>

            valueChange.addValuesAtIndex?.forEach { (index, value) ->
                try {
                    listDefinition.valueDefinition.validateWithRef(null, value) {
                        @Suppress("UNCHECKED_CAST")
                        listDefinition.itemRef(
                            index,
                            valueChange.reference as CanContainListItemReference<*, *, *>
                        )
                    }
                } catch (e: ValidationException) {
                    addException(e)
                }
            }

            valueChange.addValuesToEnd?.forEach { value ->
                try {
                    listDefinition.valueDefinition.validateWithRef(null, value) {
                        @Suppress("UNCHECKED_CAST")
                        listDefinition.anyItemRef(valueChange.reference) as IsPropertyReference<Any, *, *>
                    }
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (listValueChanges in listValueChanges) {
            listValueChanges.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val savedReferenceIndex = referenceIndex
                try {
                    val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                    return if (currentRef == null) {
                        @Suppress("UNCHECKED_CAST")
                        val list = when (newValue) {
                            is MutableList<*> -> newValue as MutableList<Any>
                            null -> mutableListOf()
                            else -> throw RequestException("Unsupported value type: $newValue for ref: $currentRef")
                        }
                        listValueChanges.deleteValues?.forEach { list.remove(it) }
                        listValueChanges.addValuesAtIndex?.forEach { (index, value) ->
                            list.add(index.toListIndex(), mutableValueCreator(value, preserveValues = true) ?: value)
                        }
                        listValueChanges.addValuesToEnd?.forEach { value ->
                            list.add(mutableValueCreator(value, preserveValues = true) ?: value)
                        }
                        if (newValue == null && list.isNotEmpty()) list else null
                    } else {
                        try {
                            deepValueChanger(originalValue, newValue, currentRef, ::valueChanger)
                        } catch (_: SubObjectChangeException) {
                            throw RequestException("Collection delta requires its parent value to exist")
                        }
                        null // Deeper change so no overwrite
                    }
                } finally {
                    referenceIndex = savedReferenceIndex
                }
            }

            when (val ref = mutableReferenceList[referenceIndex++]) {
                is IsPropertyReferenceForValues<*, *, *, *> -> objectChanger(ref, ::valueChanger)
                else -> throw RequestException("Unsupported reference type: $ref")
            }
        }
    }

    companion object : QueryModel<ListChange, Companion>() {
        val listValueChanges by list(
            index = 1u,
            getter = ListChange::listValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { ListValueChanges }
            )
        )

        override fun invoke(values: ObjectValues<ListChange, Companion>): ListChange =
            ListChange(
                listValueChanges = values(listValueChanges.index)
            )

        override val Serializer = object :
            ReferenceMappedDataModelSerializer<ListChange, ListValueChanges<*>, Companion, ListValueChanges.Companion>(
                model = this,
                containedDataModel = ListValueChanges,
                referenceProperty = ListValueChanges.reference
            ) {
            override fun writeObjectAsJson(
                obj: ListChange,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, ListChange>>?
            ) {
                writeReferenceValueMap(writer, obj.listValueChanges, context)
            }
        }
    }
}
