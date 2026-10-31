package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.models.serializers.ReferenceMappedDataModelSerializer
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines change to a set by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>) : this(setValueChange.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): SetChange? {
        val filtered = setValueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else SetChange(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        for (change in setValueChanges) {
            @Suppress("UNCHECKED_CAST")
            val definition = change.reference.comparablePropertyDefinition as IsSetDefinition<Any, IsPropertyContext>
            for (value in change.addValues.orEmpty()) {
                try {
                    definition.valueDefinition.validateWithRef(null, value) {
                        definition.itemRef(value, change.reference as CanContainSetItemReference<*, *, *>)
                    }
                } catch (exception: ValidationException) {
                    addException(exception)
                }
            }
        }
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (setValueChanges in setValueChanges) {
            setValueChanges.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val savedReferenceIndex = referenceIndex
                try {
                    val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                    return if (currentRef == null) {
                        @Suppress("UNCHECKED_CAST")
                        val set = when (newValue) {
                            is MutableSet<*> -> newValue as MutableSet<Any>
                            null -> mutableSetOf()
                            else -> throw RequestException("Unsupported value type: $newValue for ref: $currentRef")
                        }
                        setValueChanges.addValues?.let { set.addAll(it) }
                        if (newValue == null && set.isNotEmpty()) set else null
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

    companion object : QueryModel<SetChange, Companion>() {
        val setValueChanges by list(
            index = 1u,
            getter = SetChange::setValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { SetValueChanges }
            )
        )

        override fun invoke(values: ObjectValues<SetChange, Companion>): SetChange =
            SetChange(
                setValueChanges = values(setValueChanges.index)
            )

        override val Serializer = object : ReferenceMappedDataModelSerializer<SetChange, SetValueChanges<*>, Companion, SetValueChanges.Companion>(
            model = this,
            containedDataModel = SetValueChanges,
            referenceProperty = SetValueChanges.reference
        ) {
            override fun writeObjectAsJson(
                obj: SetChange,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, SetChange>>?
            ) {
                writeReferenceValueMap(writer, obj.setValueChanges, context)
            }
        }
    }
}
