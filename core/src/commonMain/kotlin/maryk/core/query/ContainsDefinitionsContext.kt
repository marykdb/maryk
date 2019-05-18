package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition

/** Describes a context which contains definitions for models and enums */
interface ContainsDefinitionsContext : IsPropertyContext {
    // All found DataModels
    val dataModels: MutableMap<String, Unit.() -> IsNamedDataModel<*>>

    // For reusing Enums in other parts
    val enums: MutableMap<String, IndexedEnumDefinition<IndexedEnum>>

    // For reusing Enums in other parts
    val typeEnums: MutableMap<String, MultiTypeEnumDefinition<MultiTypeEnum<*>>>

    // Used during parsing to inject the name
    var currentDefinitionName: String
}
