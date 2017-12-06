package maryk.core.query

import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDataObjectProperty
import maryk.core.properties.references.IsPropertyReference

class DataModelPropertyContext(
        val dataModels: Map<String, RootDataModel<*>>,
        internal var dataModel: RootDataModel<Any>? = null,
        internal var reference: IsPropertyReference<*, IsDataObjectProperty<*, *, *>>? = null
) : IsPropertyContext