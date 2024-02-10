package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType

    /** Filters the changes with [select] */
    fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): IsChange?

    /** Changes values with [objectChanger] */
    fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit)

    /** Validates the change and [addException] if it is not valid */
    fun validate(addException: (e: ValidationException) -> Unit)
}

internal val mapOfChangeDefinitions = mapOf(
    ChangeType.Change to EmbeddedObjectDefinition(dataModel = { Change }),
    ChangeType.Check to EmbeddedObjectDefinition(dataModel = { Check }),
    ChangeType.Delete to EmbeddedObjectDefinition(dataModel = { Delete }),
    ChangeType.ObjectCreate to EmbeddedObjectDefinition(dataModel = { ObjectCreate.Model }),
    ChangeType.ObjectDelete to EmbeddedObjectDefinition(dataModel = { ObjectSoftDeleteChange }),
    ChangeType.ListChange to EmbeddedObjectDefinition(dataModel = { ListChange }),
    ChangeType.SetChange to EmbeddedObjectDefinition(dataModel = { SetChange }),
    ChangeType.TypeChange to EmbeddedObjectDefinition(dataModel = { MultiTypeChange }),
    ChangeType.IncMapChange to EmbeddedObjectDefinition(dataModel = { IncMapChange }),
    ChangeType.IncMapAddition to EmbeddedObjectDefinition(dataModel = { IncMapAddition })
)
