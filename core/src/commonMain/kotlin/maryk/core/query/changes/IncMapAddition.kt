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
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef == null) {
                    when (newValue) {
                        is MutableMap<*, *> -> {
                            if (addition.addedValues == null) {
                                throw RequestException("addedValues need to be set on IncMapAddition, maybe the RequestContext of the request was not used for the response?")
                            }
                            if (addition.addedValues.size != addition.addedKeys?.size) {
                                throw RequestException("addedValues and addedKeys on IncMapAddition need to be of the same size")
                            }

                            for (index in (0..addition.addedKeys.lastIndex)) {
                                @Suppress("UNCHECKED_CAST")
                                (newValue as MutableMap<Any, Any>)[addition.addedKeys[index]] = addition.addedValues[index]
                            }
                        }
                        null -> throw RequestException("Cannot set Incrementing map changes on non existing value")
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
