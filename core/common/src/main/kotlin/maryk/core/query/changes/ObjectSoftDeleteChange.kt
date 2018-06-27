package maryk.core.query.changes

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.PropertyDefinitions

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    internal companion object: QueryDataModel<ObjectSoftDeleteChange>(
        properties = object : PropertyDefinitions<ObjectSoftDeleteChange>() {
            init {
                add(0, "isDeleted", BooleanDefinition(), ObjectSoftDeleteChange::isDeleted)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = ObjectSoftDeleteChange(
            isDeleted = map(0)
        )
    }
}
