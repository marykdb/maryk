package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinedByReference

/** Delete of a property of type [T] referred by [reference] */
data class Delete<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsPropertyDefinition<T>>
) : IsPropertyOperation<T> {
    override val changeType = ChangeType.Delete

    internal companion object: QueryDataModel<Delete<*>>(
        properties = object : PropertyDefinitions<Delete<*>>() {
            init {
                DefinedByReference.addReference(this, Delete<*>::reference)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Delete<Any>(
            reference = map(0)
        )
    }
}
