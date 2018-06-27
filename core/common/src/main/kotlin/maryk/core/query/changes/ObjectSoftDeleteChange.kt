package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.PropertyDefinitions

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    internal companion object: SimpleQueryDataModel<ObjectSoftDeleteChange>(
        properties = object : PropertyDefinitions<ObjectSoftDeleteChange>() {
            init {
                add(0, "isDeleted", BooleanDefinition(), ObjectSoftDeleteChange::isDeleted)
            }
        }
    ) {
        override fun invoke(map: DataObjectMap<ObjectSoftDeleteChange>) = ObjectSoftDeleteChange(
            isDeleted = map(0)
        )
    }
}
