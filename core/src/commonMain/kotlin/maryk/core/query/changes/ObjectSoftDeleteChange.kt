package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    internal companion object: SimpleQueryDataModel<ObjectSoftDeleteChange>(
        properties = object : ObjectPropertyDefinitions<ObjectSoftDeleteChange>() {
            init {
                add(1, "isDeleted", BooleanDefinition(), ObjectSoftDeleteChange::isDeleted)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ObjectSoftDeleteChange>) = ObjectSoftDeleteChange(
            isDeleted = values(1)
        )
    }
}
