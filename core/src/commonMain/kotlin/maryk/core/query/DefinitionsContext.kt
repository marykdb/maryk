package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition

/** Saves the context while writing and parsing Definitions */
open class DefinitionsContext(
    override val dataModels: MutableMap<String, Unit.() -> IsNamedDataModel<*>> = mutableMapOf(),
    override val enums: MutableMap<String, IndexedEnumDefinition<IndexedEnum>> = mutableMapOf(),
    override var currentDefinitionName: String = "",
    override val typeEnums: MutableMap<String, MultiTypeEnumDefinition<MultiTypeEnum<*>>> = mutableMapOf()
) : IsPropertyContext, ContainsDefinitionsContext
