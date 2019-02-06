package maryk.core.query.changes

import maryk.core.properties.definitions.EmbeddedObjectDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
    ChangeType.Change to EmbeddedObjectDefinition(dataModel = { Change }),
    ChangeType.Check to EmbeddedObjectDefinition(dataModel = { Check } ),
    ChangeType.Delete to EmbeddedObjectDefinition(dataModel = { Delete } ),
    ChangeType.ObjectDelete to EmbeddedObjectDefinition(dataModel = { ObjectSoftDeleteChange } ),
    ChangeType.ListChange to EmbeddedObjectDefinition(dataModel = { ListChange } ),
    ChangeType.SetChange to EmbeddedObjectDefinition(dataModel = { SetChange } ),
    ChangeType.TypeChange to EmbeddedObjectDefinition(dataModel = { MultiTypeChange } )
)
