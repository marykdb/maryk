package maryk.core.models.definitions

interface IsValuesDataModelDefinition : IsNamedDataModelDefinition {
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?
}
