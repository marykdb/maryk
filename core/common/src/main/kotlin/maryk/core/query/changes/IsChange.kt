package maryk.core.query.changes

import maryk.core.properties.definitions.SubModelDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
    ChangeType.Change to SubModelDefinition(dataModel = { PropertyChange }),
    ChangeType.Check to SubModelDefinition(dataModel = { PropertyCheck } ),
    ChangeType.Delete to SubModelDefinition(dataModel = { PropertyDelete } ),
    ChangeType.ObjectDelete to SubModelDefinition(dataModel = { ObjectSoftDeleteChange } ),
    ChangeType.ListChange to SubModelDefinition(dataModel = { ListPropertyChange } ),
    ChangeType.SetChange to SubModelDefinition(dataModel = { SetPropertyChange } ),
    ChangeType.MapChange to SubModelDefinition(dataModel = { MapPropertyChange } )
)
