package maryk.core.query

import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsDataModel
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition

/** Saves the context while writing and parsing Definitions */
open class DefinitionsContext(
    override val dataModels: MutableMap<String, Unit.() -> IsDataModel> = mutableMapOf(),
    override val enums: MutableMap<String, IndexedEnumDefinition<IndexedEnum>> = mutableMapOf(),
    override var currentDefinitionName: String = "",
    override val typeEnums: MutableMap<String, MultiTypeEnumDefinition<MultiTypeEnum<*>>> = mutableMapOf()
) : IsPropertyContext, ContainsDefinitionsContext
