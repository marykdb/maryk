package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Defines changes to sets by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>) : this(setValueChange.toList())

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<SetChange>() {
        val setValueChanges by list(
            index = 1u,
            getter = SetChange::setValueChanges,
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { SetValueChanges }
            )
        )
    }

    companion object : ReferenceMappedDataModel<SetChange, SetValueChanges<*>, Properties, SetValueChanges.Properties>(
        properties = Properties,
        containedDataModel = SetValueChanges,
        referenceProperty = SetValueChanges.Properties.reference
    ) {
        override fun invoke(values: ObjectValues<SetChange, Properties>) = SetChange(
            setValueChanges = values(1u)
        )

        override fun writeJson(obj: SetChange, writer: IsJsonLikeWriter, context: RequestContext?) {
            writeReferenceValueMap(writer, obj.setValueChanges, context)
        }
    }
}
