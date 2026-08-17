package maryk.datastore.foundationdb.model

internal val modelNameKey = byteArrayOf(0)
internal val modelVersionKey = byteArrayOf(1)
internal val modelDefinitionKey = byteArrayOf(2)
internal val modelDependentsDefinitionKey = byteArrayOf(3)
internal val modelMigrationStateKey = byteArrayOf(4)
internal val modelMigrationLeaseKey = byteArrayOf(5)
internal val modelMigrationAuditLogKey = byteArrayOf(6)
internal val modelUpdateHistoryBackfillCompleteKey = byteArrayOf(7)
/**
 * A model-local epoch. Normal writers read it in their write transaction so a
 * schema transition conflicts with already-running writers and rejects old
 * stores after publication.
 */
internal val modelSchemaStateKey = byteArrayOf(8)
/** Transition-owned scratch space for bounded index-rebuild replay. */
internal val modelIndexRebuildScratchKey = byteArrayOf(9)
/** Versioned chunks for migration audit logs which exceed FoundationDB's value limit. */
internal val modelMigrationAuditLogChunksKey = byteArrayOf(10)
internal val modelReplicationTombstoneBackfillCompleteKey = byteArrayOf(11)
