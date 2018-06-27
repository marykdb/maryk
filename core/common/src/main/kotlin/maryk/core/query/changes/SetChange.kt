package maryk.core.query.changes

import maryk.core.models.ReferenceMappedDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Defines changes to sets by [setValueChanges] */
data class SetChange internal constructor(
    val setValueChanges: List<SetValueChanges<*>>
) : IsChange {
    override val changeType = ChangeType.SetChange

    constructor(vararg setValueChange: SetValueChanges<*>): this(setValueChange.toList())

    internal object Properties : PropertyDefinitions<SetChange>() {
        init {
            add(0, "setValueChanges",
                ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { SetValueChanges }
                    )
                ),
                SetChange::setValueChanges
            )
        }
    }

    internal companion object: ReferenceMappedDataModel<SetChange, SetValueChanges<*>, Properties, SetValueChanges.Properties>(
        properties = SetChange.Properties,
        containedDataModel = SetValueChanges,
        referenceProperty = SetValueChanges.Properties.reference
    ) {
        override fun invoke(map: DataObjectMap<SetChange>) = SetChange(
            setValueChanges = map(0)
        )

        override fun writeJson(map: DataObjectMap<SetChange>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            @Suppress("UNCHECKED_CAST")
            writeReferenceValueMap(
                writer,
                map[0] as List<SetValueChanges<*>>,
                context
            )
        }

        override fun writeJson(obj: SetChange, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writeReferenceValueMap(writer, obj.setValueChanges, context)
        }
    }
}
