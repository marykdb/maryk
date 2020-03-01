package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.graph.RootPropRefGraph
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

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): IncMapAddition? {
        val filtered = additions.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else IncMapAddition(filtered)
    }

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<IncMapAddition>() {
        val additions by list(
            index = 1u,
            getter = IncMapAddition::additions,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { IncMapKeyAdditions }
            )
        )
    }

    companion object : ReferenceMappedDataModel<IncMapAddition, IncMapKeyAdditions<out Comparable<Any>, out Any>, Properties, IncMapKeyAdditions.Properties>(
        properties = Properties,
        containedDataModel = IncMapKeyAdditions,
        referenceProperty = IncMapKeyAdditions.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<IncMapAddition, Properties>) = IncMapAddition(
            additions = values(1u)
        )

        override fun writeJson(obj: IncMapAddition, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.additions, context)
        }
    }
}
