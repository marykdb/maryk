package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.ObjectPropertyDefinitions

/** Defines a change in DataObject Soft Delete state to [isDeleted] */
data class ObjectSoftDeleteChange(
    val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.ObjectDelete

    internal companion object: SimpleQueryDataModel<ObjectSoftDeleteChange>(
        properties = object : ObjectPropertyDefinitions<ObjectSoftDeleteChange>() {
            init {
                add(0, "isDeleted", BooleanDefinition(), ObjectSoftDeleteChange::isDeleted)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ObjectSoftDeleteChange>) = ObjectSoftDeleteChange(
            isDeleted = map(0)
        )
    }
}
