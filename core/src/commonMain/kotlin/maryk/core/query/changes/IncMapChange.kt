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
import maryk.core.properties.types.numeric.NumberDescriptor
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines [valueChanges] to incrementing maps  */
data class IncMapChange internal constructor(
    val valueChanges: List<IncMapValueChanges<out Comparable<Any>, out Any>>
) : IsChange {
    override val changeType = ChangeType.IncMapChange

    @Suppress("UNCHECKED_CAST")
    constructor(vararg valueChanges: IncMapValueChanges<*, out Any>) : this(valueChanges.toList() as List<IncMapValueChanges<out Comparable<Any>, out Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): IncMapChange? {
        val filtered = valueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else IncMapChange(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (valueChange in this.valueChanges) {
            valueChange.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)
                @Suppress("UNCHECKED_CAST")
                val descriptor = (valueChange.reference.propertyDefinition.definition.keyNumberDescriptor as NumberDescriptor<Comparable<Any>>)
                val one = descriptor.ofInt(1)

                return if (currentRef == null) {
                    when (newValue) {
                        is MutableMap<*, *> -> {
                            valueChange.addValues?.let { addValues ->
                                @Suppress("UNCHECKED_CAST")
                                var latestKeyedItem = (newValue as MutableMap<Comparable<Any>, Any>).maxBy { it.key }!!.key

                                for (value in addValues) {
                                    latestKeyedItem = descriptor.sum(latestKeyedItem, one)
                                    newValue.put(latestKeyedItem, value)
                                }
                            }
                        }
                        null -> throw RequestException("Cannot set incrementing map changes on non existing value")
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
    object Properties : ObjectPropertyDefinitions<IncMapChange>() {
        val valueChanges by list(
            index = 1u,
            getter = IncMapChange::valueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { IncMapValueChanges }
            )
        )
    }

    companion object : ReferenceMappedDataModel<IncMapChange, IncMapValueChanges<out Comparable<Any>, out Any>, Properties, IncMapValueChanges.Properties>(
        properties = Properties,
        containedDataModel = IncMapValueChanges,
        referenceProperty = IncMapValueChanges.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<IncMapChange, Properties>) = IncMapChange(
            valueChanges = values<List<IncMapValueChanges<out Comparable<Any>, out Any>>>(1u)
        )

        override fun writeJson(obj: IncMapChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.valueChanges, context)
        }
    }
}
