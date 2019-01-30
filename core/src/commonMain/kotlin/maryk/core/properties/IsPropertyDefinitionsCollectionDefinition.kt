package maryk.core.properties

import maryk.core.query.DefinitionsConversionContext

internal interface IsPropertyDefinitionsCollectionDefinition<P: IsPropertyDefinitions> {
    val capturer: (DefinitionsConversionContext?, P) -> Unit
}
