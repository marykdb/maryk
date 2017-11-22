package maryk.core.query.properties.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.BooleanDefinition

/** Defines a change in DataObject Soft Delete state
 * @param isDeleted true if DataObject was soft deleted and false if not
 */
data class ObjectSoftDeleteChange(
        val isDeleted: Boolean
) : IsChange {
    object Properties {
        val isDeleted = BooleanDefinition(
                name = "isDeleted",
                index = 0
        )
    }

    companion object: QueryDataModel<ObjectSoftDeleteChange>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ObjectSoftDeleteChange(
                        isDeleted = it[0] as Boolean
                )
            },
            definitions = listOf(
                    Def(Properties.isDeleted, ObjectSoftDeleteChange::isDeleted)
            )
    )
}