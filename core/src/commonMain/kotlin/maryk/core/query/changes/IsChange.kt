package maryk.core.query.changes

import maryk.core.properties.IsRootModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import kotlin.native.concurrent.SharedImmutable

/** Defines a change to a DataObject */
interface IsChange {
    val changeType: ChangeType

    /** Filters the changes with [select] */
    fun filterWithSelect(select: RootPropRefGraph<out IsRootModel>): IsChange?

    /** Changes values with [objectChanger] */
    fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit)
}

@SharedImmutable
internal val mapOfChangeDefinitions = mapOf(
    ChangeType.Change to EmbeddedObjectDefinition(dataModel = { Change.Model }),
    ChangeType.Check to EmbeddedObjectDefinition(dataModel = { Check.Model }),
    ChangeType.Delete to EmbeddedObjectDefinition(dataModel = { Delete }),
    ChangeType.ObjectCreate to EmbeddedObjectDefinition(dataModel = { ObjectCreateModel }),
    ChangeType.ObjectDelete to EmbeddedObjectDefinition(dataModel = { ObjectSoftDeleteChange }),
    ChangeType.ListChange to EmbeddedObjectDefinition(dataModel = { ListChange }),
    ChangeType.SetChange to EmbeddedObjectDefinition(dataModel = { SetChange }),
    ChangeType.TypeChange to EmbeddedObjectDefinition(dataModel = { MultiTypeChange.Model }),
    ChangeType.IncMapChange to EmbeddedObjectDefinition(dataModel = { IncMapChange }),
    ChangeType.IncMapAddition to EmbeddedObjectDefinition(dataModel = { IncMapAddition })
)
