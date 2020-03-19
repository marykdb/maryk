package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.boolean
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.values.SimpleObjectValues

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    override fun filterWithSelect(select: RootPropRefGraph<out PropertyDefinitions>): ObjectSoftDeleteChange? {
        // Not influenced by select
        return this
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Do nothing since it cannot operate on object itself
    }

    internal companion object : SimpleQueryDataModel<ObjectSoftDeleteChange>(
        properties = object : ObjectPropertyDefinitions<ObjectSoftDeleteChange>() {
            val isDeleted by boolean(1u, ObjectSoftDeleteChange::isDeleted)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ObjectSoftDeleteChange>) = ObjectSoftDeleteChange(
            isDeleted = values(1u)
        )
    }
}
