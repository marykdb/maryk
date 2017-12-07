package maryk.core.query.changes

import maryk.core.properties.definitions.SubModelDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
        ChangeType.PROP_CHANGE.index to SubModelDefinition(
                dataModel = PropertyChange
        ),
        ChangeType.PROP_CHECK.index to SubModelDefinition(
                dataModel = PropertyCheck
        ),
        ChangeType.PROP_DELETE.index to SubModelDefinition(
                dataModel = PropertyDelete
        ),
        ChangeType.OBJECT_DELETE.index to SubModelDefinition(
                dataModel = ObjectSoftDeleteChange
        ),
        ChangeType.LIST_CHANGE.index to SubModelDefinition(
                dataModel = ListPropertyChange
        ),
        ChangeType.SET_CHANGE.index to SubModelDefinition(
                dataModel = SetPropertyChange
        ),
        ChangeType.MAP_CHANGE.index to SubModelDefinition(
                dataModel = MapPropertyChange
        )
)