package maryk.core.query.changes

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryModel
import maryk.core.models.serializers.ReferenceMappedDataModelSerializer
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IncMapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
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

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): IncMapChange? {
        val filtered = valueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else IncMapChange(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        valueChanges.forEach { (reference, addedValues) ->
            @Suppress("UNCHECKED_CAST")
            val mapDefinition = reference.comparablePropertyDefinition as IncMapDefinitionWrapper<Comparable<Any>, Any, *, *, *>
            if (addedValues != null) {
                for (value in addedValues) {
                    try {
                        mapDefinition.valueDefinition.validateWithRef(null, value) {
                            @Suppress("UNCHECKED_CAST")
                            mapDefinition.anyValueRef(reference) as? IsPropertyReference<Any, *, *>
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (valueChange in this.valueChanges) {
            valueChange.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef == null) {
                    when (newValue) {
                        is MutableMap<*, *> -> {
                            valueChange.addValues?.let { addValues ->
                                appendValues(valueChange.reference, newValue, addValues)
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

    companion object : QueryModel<IncMapChange, Companion>() {
        val valueChanges by list(
            index = 1u,
            getter = IncMapChange::valueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { IncMapValueChanges }
            )
        )

        override fun invoke(values: ObjectValues<IncMapChange, Companion>): IncMapChange =
            IncMapChange(
                valueChanges = values<List<IncMapValueChanges<out Comparable<Any>, out Any>>>(valueChanges.index)
            )

        override val Serializer = object : ReferenceMappedDataModelSerializer<IncMapChange, IncMapValueChanges<out Comparable<Any>, out Any>, Companion, IncMapValueChanges.Companion>(
            model = this,
            containedDataModel = IncMapValueChanges,
            referenceProperty = IncMapValueChanges.reference
        ) {
            override fun writeObjectAsJson(
                obj: IncMapChange,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, IncMapChange>>?
            ) {
                writeReferenceValueMap(writer, obj.valueChanges, context)
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <K : Comparable<K>, V : Any> appendValues(
    reference: IncMapReference<K, V, *>,
    map: MutableMap<*, *>,
    addValues: List<*>
) {
    val descriptor: NumberDescriptor<K> = reference.propertyDefinition.definition.keyNumberDescriptor
    val one = descriptor.ofInt(1)
    val typedMap = map as MutableMap<K, V>
    var latestKeyedItem = typedMap.maxByOrNull { it.key }?.key ?: descriptor.zero
    for (value in addValues) {
        latestKeyedItem = descriptor.sum(latestKeyedItem, one)
        typedMap[latestKeyedItem] = value as V
    }
}
