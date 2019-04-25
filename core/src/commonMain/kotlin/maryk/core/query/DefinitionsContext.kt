package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Saves the context while writing and parsing Definitions */
open class DefinitionsContext(
    override val dataModels: MutableMap<String, Unit.() -> IsNamedDataModel<*>> = mutableMapOf(),
    override val enums: MutableMap<String, IndexedEnumDefinition<IndexedEnum>> = mutableMapOf(),
    override var currentDefinitionName: String = ""
) : IsPropertyContext, ContainsDefinitionsContext
