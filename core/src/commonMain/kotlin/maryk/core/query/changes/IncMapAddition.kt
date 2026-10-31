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
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.mutableValueCreator
import maryk.json.IsJsonLikeWriter

/** Defines [additions] to incrementing maps  */
data class IncMapAddition(
    val additions: List<IncMapKeyAdditions<out Comparable<Any>, out Any>>
) : IsChange {
    override val changeType = ChangeType.IncMapAddition

    @Suppress("UNCHECKED_CAST")
    constructor(vararg valueChanges: IncMapKeyAdditions<*, out Any>) : this(valueChanges.toList() as List<IncMapKeyAdditions<out Comparable<Any>, out Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): IncMapAddition? {
        val filtered = additions.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else IncMapAddition(filtered)
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        additions.forEach { (reference, addedKeys, addedValues) ->
            @Suppress("UNCHECKED_CAST")
            val mapDefinition = reference.comparablePropertyDefinition as IncMapDefinitionWrapper<Comparable<Any>, Any, *, *, *>
            if (addedKeys != null && addedValues != null) {
                for ((index, key) in addedKeys.withIndex()) {
                    try {
                        mapDefinition.keyDefinition.validateWithRef(null, key) {
                            mapDefinition.keyRef(key, reference)
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                    try {
                        mapDefinition.valueDefinition.validateWithRef(null, addedValues[index]) {
                            mapDefinition.valueRef(key, reference)
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

        for (addition in this.additions) {
            addition.reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueChanger(originalValue: Any?, newValue: Any?): Any? {
                val savedReferenceIndex = referenceIndex
                try {
                    val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                    return if (currentRef == null) {
                        @Suppress("UNCHECKED_CAST")
                        val map = when (newValue) {
                            is MutableMap<*, *> -> newValue as MutableMap<Any, Any>
                            null -> mutableMapOf()
                            else -> throw RequestException("Unsupported value type: $newValue for ref: $currentRef")
                        }
                        val values = addition.addedValues
                            ?: throw RequestException("addedValues need to be set on IncMapAddition")
                        val keys = addition.addedKeys
                            ?: throw RequestException("addedKeys need to be set on IncMapAddition")
                        if (values.size != keys.size) throw RequestException("addedValues and addedKeys need the same size")
                        for (index in keys.indices) {
                            val value = values[index]
                            map[keys[index]] = mutableValueCreator(value, preserveValues = true) ?: value
                        }
                        if (newValue == null && map.isNotEmpty()) map else null
                    } else {
                        try {
                            deepValueChanger(originalValue, newValue, currentRef, ::valueChanger)
                        } catch (_: SubObjectChangeException) {
                            throw RequestException("Map addition requires its parent value to exist")
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

    companion object : QueryModel<IncMapAddition, Companion>() {
        val additions by list(
            index = 1u,
            getter = IncMapAddition::additions,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { IncMapKeyAdditions }
            )
        )

        override fun invoke(values: ObjectValues<IncMapAddition, Companion>): IncMapAddition =
            IncMapAddition(
                additions = values(additions.index)
            )

        override val Serializer = object: ReferenceMappedDataModelSerializer<IncMapAddition, IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion, IncMapKeyAdditions.Companion>(
            this,
            containedDataModel = IncMapKeyAdditions,
            referenceProperty = IncMapKeyAdditions.reference,
        ) {
            override fun writeObjectAsJson(
                obj: IncMapAddition,
                writer: IsJsonLikeWriter,
                context: RequestContext?,
                skip: List<IsDefinitionWrapper<*, *, *, IncMapAddition>>?
            ) {
                writeReferenceValueMap(writer, obj.additions, context)
            }
        }
    }
}
