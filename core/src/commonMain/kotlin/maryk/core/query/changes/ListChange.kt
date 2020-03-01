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

/** Defines changes to lists by [listValueChanges] */
data class ListChange internal constructor(
    val listValueChanges: List<ListValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.ListChange

    constructor(vararg listValueChange: ListValueChanges<*>) : this(listValueChange.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): ListChange? {
        val filtered = listValueChanges.filter {
            select.contains(it.reference)
        }
        return if (filtered.isEmpty()) null else ListChange(filtered)
    }

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ListChange>() {
        val referenceListValueChangesPairs by list(
            index = 1u,
            getter = ListChange::listValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { ListValueChanges }
            )
        )
    }

    companion object :
        ReferenceMappedDataModel<ListChange, ListValueChanges<*>, Properties, ListValueChanges.Properties>(
            properties = Properties,
            containedDataModel = ListValueChanges,
            referenceProperty = ListValueChanges.Properties.reference
        ) {
        override fun invoke(values: ObjectValues<ListChange, Properties>) = ListChange(
            listValueChanges = values(1u)
        )

        override fun writeJson(obj: ListChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.listValueChanges, context)
        }
    }
}
