package maryk.core.query

/** Persisted schema metadata retains reference widths independently of target registration. */
open class StorageDefinitionsConversionContext(
    definitionsContext: DefinitionsContext = DefinitionsContext(),
) : DefinitionsConversionContext(definitionsContext)
