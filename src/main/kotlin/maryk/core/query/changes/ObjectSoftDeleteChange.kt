package maryk.core.query.changes

import maryk.core.objects.Def
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

    internal object Properties : PropertyDefinitions<ObjectSoftDeleteChange>() {
        val isDeleted = BooleanDefinition(
                name = "isDeleted",
                index = 0
        )
    }

    companion object: QueryDataModel<ObjectSoftDeleteChange>(
            definitions = listOf(
                    Def(Properties.isDeleted, ObjectSoftDeleteChange::isDeleted)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = ObjectSoftDeleteChange(
                isDeleted = map[0] as Boolean
        )
    }
}