package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.values.SimpleObjectValues

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): ObjectSoftDeleteChange {
        // Not influenced by select
        return this
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Do nothing since it cannot operate on object itself
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        // Always valid
    }

    override fun toString() = "ObjectSoftDelete[$isDeleted]"

    internal companion object : SimpleQueryModel<ObjectSoftDeleteChange>() {
        val isDeleted by boolean(1u, ObjectSoftDeleteChange::isDeleted)

        override fun invoke(values: SimpleObjectValues<ObjectSoftDeleteChange>) = ObjectSoftDeleteChange(
            isDeleted = values(1u)
        )
    }
}
