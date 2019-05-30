package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
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

    object Properties : ObjectPropertyDefinitions<IncMapAddition>() {
        init {
            add(1u, "additions",
                ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { IncMapKeyAdditions }
                    )
                ),
                IncMapAddition::additions
            )
        }
    }

    companion object : ReferenceMappedDataModel<IncMapAddition, IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties, IncMapKeyAdditions.Properties>(
        properties = Properties,
        containedDataModel = IncMapKeyAdditions,
        referenceProperty = IncMapKeyAdditions.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<IncMapAddition, Properties>) = IncMapAddition(
            additions = values<List<IncMapKeyAdditions<out Comparable<Any>, out Any>>>(1u)
        )

        override fun writeJson(obj: IncMapAddition, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.additions, context)
        }
    }
}
