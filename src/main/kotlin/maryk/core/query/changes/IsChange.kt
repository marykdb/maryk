package maryk.core.query.changes

import maryk.core.properties.definitions.SubModelDefinition

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType
}

internal val mapOfChangeDefinitions = mapOf(
        ChangeType.PROP_CHANGE.index to SubModelDefinition(
                required = true,
                dataModel = PropertyChange
        ),
        ChangeType.PROP_CHECK.index to SubModelDefinition(
                required = true,
                dataModel = PropertyCheck
        ),
        ChangeType.PROP_DELETE.index to SubModelDefinition(
                required = true,
                dataModel = PropertyDelete
        ),
        ChangeType.OBJECT_DELETE.index to SubModelDefinition(
                required = true,
                dataModel = ObjectSoftDeleteChange
        ),
        ChangeType.LIST_CHANGE.index to SubModelDefinition(
                required = true,
                dataModel = ListPropertyChange
        ),
        ChangeType.SET_CHANGE.index to SubModelDefinition(
                required = true,
                dataModel = SetPropertyChange
        ),
        ChangeType.MAP_CHANGE.index to SubModelDefinition(
                required = true,
                dataModel = MapPropertyChange
        )
)