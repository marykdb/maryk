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

/** Defines [additions] to incrementing maps  */
data class IncMapAddition(
    val additions: List<IncMapKeyAdditions<out Comparable<Any>, out Any>>
) : IsChange {
    override val changeType = ChangeType.IncMapAddition

    @Suppress("UNCHECKED_CAST")
    constructor(vararg valueChanges: IncMapKeyAdditions<*, out Any>) : this(valueChanges.toList() as List<IncMapKeyAdditions<out Comparable<Any>, out Any>>)

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootModel>): IncMapAddition? {
        val filtered = additions.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else IncMapAddition(filtered)
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

    @Suppress("unused")
    companion object : QueryModel<IncMapAddition, Companion>() {
        val additions by list(
            index = 1u,
            getter = IncMapAddition::additions,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { IncMapKeyAdditions.Model }
            )
        )

        override fun invoke(values: ObjectValues<IncMapAddition, Companion>): IncMapAddition =
            Model.invoke(values)

        override val Model = object : ReferenceMappedDataModel<IncMapAddition, IncMapKeyAdditions<out Comparable<Any>, out Any>, Companion, IncMapKeyAdditions.Companion>(
            properties = Companion,
            containedDataModel = IncMapKeyAdditions.Model,
            referenceProperty = IncMapKeyAdditions.reference
        ) {
            override fun invoke(values: ObjectValues<IncMapAddition, Companion>) = IncMapAddition(
                additions = values(1u)
            )

            override fun writeJson(obj: IncMapAddition, writer: IsJsonLikeWriter, context: RequestContext?) {
                writeReferenceValueMap(writer, obj.additions, context)
            }
        }
    }
}
