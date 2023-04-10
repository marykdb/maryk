package maryk.core.models.definitions

/**
 * Data model definition for models which defines objects which are represented
 * by Values. Each property in such Values has a name and index.
 * This definition defines which indices and names are reserved because of past
 * migrations so no conflicts arise.
 */
interface IsValuesDataModelDefinition : IsDataModelDefinition {
    /** List of indices which are reserved for use. Init will fail if they are used */
    val reservedIndices: List<UInt>?
    /** List of names which are reserved for use. Init will fail if they are used */
    val reservedNames: List<String>?
}
