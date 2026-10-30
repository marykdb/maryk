package maryk.core.models.migration

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.KeyValueDefinitionContext
import maryk.core.properties.definitions.ListDefinitionContext
import maryk.core.properties.definitions.SetDefinitionContext
import maryk.core.properties.definitions.contextual.ModelContext
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.types.TimePrecision
import maryk.core.query.DefinitionsConversionContext
import maryk.core.query.RequestContext

/** Persisted legacy logs need conversion or explicit rejection when their value transport changed. */
fun IsValuesDataModel.hasLegacyTransportStorage(): Boolean =
    hasFloatingPointValueObjects() || hasLegacyTransportDefinitions(mutableSetOf())

private fun IsValuesDataModel.hasLegacyTransportDefinitions(visited: MutableSet<IsValuesDataModel>): Boolean =
    visited.add(this) && any { it.definition.hasLegacyTransportDefinitions(visited) }

private fun IsPropertyDefinition<*>.hasLegacyTransportDefinitions(visited: MutableSet<IsValuesDataModel>): Boolean = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.hasLegacyTransportDefinitions(visited)
    is DateTimeDefinition -> precision == TimePrecision.NANOS
    is IsEmbeddedValuesDefinition<*, *> -> dataModel.hasLegacyTransportDefinitions(visited)
    is IncrementingMapDefinition<*, *, *> -> valueDefinition.isCollectionTransportValue() ||
        valueDefinition.hasLegacyTransportDefinitions(visited)
    is IsMapDefinition<*, *, *> -> keyDefinition.hasLegacyTransportDefinitions(visited) || valueDefinition.hasLegacyTransportDefinitions(visited)
    is IsCollectionDefinition<*, *, *, *> -> valueDefinition.hasLegacyTransportDefinitions(visited)
    is IsMultiTypeDefinition<*, *, *> -> typeEnum.cases().any {
        (it as MultiTypeEnum<*>).definition?.hasLegacyTransportDefinitions(visited) == true
    }
    else -> false
}

private fun IsPropertyDefinition<*>.isCollectionTransportValue(): Boolean = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.isCollectionTransportValue()
    is IsCollectionDefinition<*, *, *, *>, is IsMapDefinition<*, *, *> -> true
    else -> false
}

/** Use only when enclosing store/log metadata establishes the released legacy format. */
fun <T> RequestContext.readLegacyTransportStorage(read: () -> T): T {
    val previous = legacyNanosDateTime
    legacyNanosDateTime = true
    return try { readLegacyValueStorage(read) } finally { legacyNanosDateTime = previous }
}

/** Stored source schemas can themselves contain defaults encoded with the legacy codecs. */
fun <T> DefinitionsConversionContext.readLegacyTransportStorage(read: () -> T): T {
    val previous = legacyTransportStorage
    legacyTransportStorage = true
    return try { read() } finally { legacyTransportStorage = previous }
}

internal fun IsPropertyContext?.readsLegacyValueStorage(): Boolean =
    if (this is RequestContext) legacyValueStorage else readsLegacyDefinitionStorage()

internal fun IsPropertyContext?.readsLegacyNanosDateTime(): Boolean = readsLegacyTransportStorage()

internal fun IsPropertyContext?.readsLegacyTransportStorage(): Boolean =
    if (this is RequestContext) legacyNanosDateTime else readsLegacyDefinitionStorage()

private fun IsPropertyContext?.readsLegacyDefinitionStorage(): Boolean = when (this) {
    is DefinitionsConversionContext -> legacyTransportStorage
    is ListDefinitionContext -> definitionsContext.readsLegacyDefinitionStorage()
    is SetDefinitionContext -> definitionsContext.readsLegacyDefinitionStorage()
    is KeyValueDefinitionContext -> definitionsContext.readsLegacyDefinitionStorage()
    is ModelContext -> definitionsContext.readsLegacyDefinitionStorage()
    is MultiTypeDefinitionContext -> definitionsContext.readsLegacyDefinitionStorage()
    else -> false
}
