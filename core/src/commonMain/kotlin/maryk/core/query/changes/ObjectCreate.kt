package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.values.SimpleObjectValues

/** The creation of a DataObject */
object ObjectCreate : IsChange {
    override val changeType = ChangeType.ObjectCreate

    override fun filterWithSelect(select: RootPropRefGraph<out IsValuesPropertyDefinitions>): ObjectCreate {
        // Not influenced by select
        return this
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Do nothing since it cannot operate on object itself
    }

    override fun toString() = "ObjectCreate"
}

internal object ObjectCreateModel: SimpleQueryDataModel<ObjectCreate>(
    properties = object : ObjectPropertyDefinitions<ObjectCreate>() {}
) {
    override fun invoke(values: SimpleObjectValues<ObjectCreate>) = ObjectCreate
}
