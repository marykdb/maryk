package maryk.core.query.changes

import maryk.core.properties.definitions.SubModelDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
    ChangeType.PROP_CHANGE to SubModelDefinition(dataModel = { PropertyChange }),
    ChangeType.PROP_CHECK to SubModelDefinition(dataModel = { PropertyCheck } ),
    ChangeType.PROP_DELETE to SubModelDefinition(dataModel = { PropertyDelete } ),
    ChangeType.OBJECT_DELETE to SubModelDefinition(dataModel = { ObjectSoftDeleteChange } ),
    ChangeType.LIST_CHANGE to SubModelDefinition(dataModel = { ListPropertyChange } ),
    ChangeType.SET_CHANGE to SubModelDefinition(dataModel = { SetPropertyChange } ),
    ChangeType.MAP_CHANGE to SubModelDefinition(dataModel = { MapPropertyChange } )
)