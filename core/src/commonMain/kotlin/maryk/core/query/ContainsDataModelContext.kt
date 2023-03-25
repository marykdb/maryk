package maryk.core.query

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions

interface ContainsDataModelContext<out DM : IsPropertyDefinitions> : IsPropertyContext {
    val dataModel: DM?
}
