package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnumDefinition

/** Describes a context which contains definitions for models and enums */
interface ContainsDefinitionsContext: IsPropertyContext {
    val dataModels: MutableMap<String, Unit.() -> IsNamedDataModel<*>>
    val enums: MutableMap<String, IndexedEnumDefinition<*>>
}
