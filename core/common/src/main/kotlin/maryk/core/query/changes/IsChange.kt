package maryk.core.query.changes

import maryk.core.properties.definitions.SubModelDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
    ChangeType.Change to SubModelDefinition(dataModel = { Change }),
    ChangeType.Check to SubModelDefinition(dataModel = { Check } ),
    ChangeType.Delete to SubModelDefinition(dataModel = { Delete } ),
    ChangeType.ObjectDelete to SubModelDefinition(dataModel = { ObjectSoftDeleteChange } ),
    ChangeType.ListChange to SubModelDefinition(dataModel = { ListChange } ),
    ChangeType.SetChange to SubModelDefinition(dataModel = { SetChange } ),
    ChangeType.MapChange to SubModelDefinition(dataModel = { MapChange } )
)
