package maryk.core.query

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.enum.EnumNameContext
import maryk.core.properties.definitions.KeyValueDefinitionContext
import maryk.core.properties.definitions.ListDefinitionContext
import maryk.core.properties.definitions.SetDefinitionContext
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext

/** Canonical collection ordering used only to identify a migration's complete target schema. */
class MigrationDefinitionsConversionContext : StorageDefinitionsConversionContext()

internal fun IsPropertyContext?.writesMigrationSchemaIdentity(): Boolean = when (this) {
    is MigrationDefinitionsConversionContext -> true
    is EnumNameContext -> definitionsContext.writesMigrationSchemaIdentity()
    is ListDefinitionContext -> definitionsContext.writesMigrationSchemaIdentity()
    is SetDefinitionContext -> definitionsContext.writesMigrationSchemaIdentity()
    is KeyValueDefinitionContext -> definitionsContext.writesMigrationSchemaIdentity()
    is ModelContext -> definitionsContext.writesMigrationSchemaIdentity()
    is MultiTypeDefinitionContext -> definitionsContext.writesMigrationSchemaIdentity()
    else -> false
}
