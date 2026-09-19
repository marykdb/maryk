package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.values.SimpleObjectValues

/** The creation of a DataObject */
object ObjectCreate : IsChange {
    override val changeType = ChangeType.ObjectCreate

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>): ObjectCreate {
        // Not influenced by select
        return this
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Do nothing since it cannot operate on object itself
    }

    override fun validate(addException: (e: ValidationException) -> Unit) {
        // Always valid
    }

    override fun toString() = "ObjectCreate"

    val Model = object : SimpleQueryModel<ObjectCreate>() {
        override fun invoke(values: SimpleObjectValues<ObjectCreate>) = ObjectCreate
    }
}
