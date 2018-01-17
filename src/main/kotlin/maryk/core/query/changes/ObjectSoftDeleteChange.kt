package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.PropertyDefinitions

/** Defines a change in DataObject Soft Delete state
 * @param isDeleted true if DataObject was soft deleted and false if not
 */
data class ObjectSoftDeleteChange(
        val isDeleted: Boolean
) : IsChange {
    override val changeType = ChangeType.OBJECT_DELETE

    companion object: QueryDataModel<ObjectSoftDeleteChange>(
            properties = object : PropertyDefinitions<ObjectSoftDeleteChange>() {
                init {
                    add(0, "isDeleted", BooleanDefinition(), ObjectSoftDeleteChange::isDeleted)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = ObjectSoftDeleteChange(
                isDeleted = map[0] as Boolean
        )
    }
}