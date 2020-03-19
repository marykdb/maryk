package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
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
        TODO("Not yet implemented")
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
