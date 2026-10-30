package maryk.datastore.shared.migration

import maryk.core.models.IsValuesDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.models.migration.convertLegacyStorageQualifier
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum

/** Count/tombstone collisions also affect models with no floating-point properties. */
fun IsValuesDataModel.hasLegacyCollectionStorage(): Boolean = hasCollections(mutableSetOf())

fun IsValuesDataModel.hasLegacyIncrementingMapStorage(): Boolean = hasIncrementingMaps(mutableSetOf())

private fun IsValuesDataModel.hasIncrementingMaps(visited: MutableSet<IsValuesDataModel>): Boolean =
    visited.add(this) && any { it.definition.hasIncrementingMaps(visited) }

private fun IsPropertyDefinition<*>.hasIncrementingMaps(visited: MutableSet<IsValuesDataModel>): Boolean = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.hasIncrementingMaps(visited)
    is IncrementingMapDefinition<*, *, *> -> true
    is IsMapDefinition<*, *, *> -> valueDefinition.hasIncrementingMaps(visited)
    is IsCollectionDefinition<*, *, *, *> -> valueDefinition.hasIncrementingMaps(visited)
    is IsEmbeddedValuesDefinition<*, *> -> dataModel.hasIncrementingMaps(visited)
    is IsMultiTypeDefinition<*, *, *> -> typeEnum.cases().any { (it as MultiTypeEnum<*>).definition?.hasIncrementingMaps(visited) == true }
    else -> false
}

private fun IsValuesDataModel.hasCollections(visited: MutableSet<IsValuesDataModel>): Boolean =
    visited.add(this) && any { it.definition.hasCollections(visited) }

private fun IsPropertyDefinition<*>.hasCollections(visited: MutableSet<IsValuesDataModel>): Boolean = when (this) {
    is IsDefinitionWrapper<*, *, *, *> -> definition.hasCollections(visited)
    is IsCollectionDefinition<*, *, *, *>, is IsMapDefinition<*, *, *> -> true
    is IsEmbeddedValuesDefinition<*, *> -> dataModel.hasCollections(visited)
    is IsMultiTypeDefinition<*, *, *> -> typeEnum.cases().any { (it as MultiTypeEnum<*>).definition?.hasCollections(visited) == true }
    else -> false
}

/** Ordinary counts are untagged: [0] is valid and cannot itself prove deletion. */
fun IsValuesDataModel.isLegacyZeroCollectionCount(qualifier: ByteArray, payload: ByteArray): Boolean {
    if (payload.size != 1 || payload[0] != 0.toByte() || qualifier.isEmpty()) return false
    val resolved = convertLegacyStorageQualifier(qualifier)
    return !resolved.valueCell && (resolved.definition is IsCollectionDefinition<*, *, *, *> || resolved.definition is IsMapDefinition<*, *, *>)
}

/** Include durable identities so an operator can recover from an authoritative source. */
fun ambiguousLegacyCollectionCount(modelId: UInt, qualifier: ByteArray, version: ByteArray): Nothing =
    throw MigrationException("Cannot distinguish a released empty collection from a deleted collection: model=$modelId " +
        "qualifier=${qualifier.toHexString()} version=${version.toHexString()}. Source rows are unchanged. " +
        "Recover this collection's presence from an authoritative application log or backup before reopening; do not guess from aggregate counts.")
