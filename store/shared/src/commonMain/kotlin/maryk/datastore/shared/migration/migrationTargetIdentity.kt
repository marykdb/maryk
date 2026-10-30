package maryk.datastore.shared.migration

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import maryk.core.definitions.Definitions
import maryk.core.definitions.MarykPrimitive
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.migration.MigrationException
import maryk.core.protobuf.WriteCache
import maryk.core.query.MigrationDefinitionsConversionContext

/** Stable identity of the complete target definition, using the persisted schema codec. */
suspend fun migrationTargetIdentity(dataModel: IsRootDataModel): ByteArray {
    val context = MigrationDefinitionsConversionContext()
    val root = dataModel as RootDataModel<*>
    val rootCache = WriteCache()
    val rootSize = RootDataModel.Model.Serializer.calculateObjectProtoBufLength(root, rootCache, context)
    val rootBytes = ByteArray(rootSize)
    var offset = 0
    RootDataModel.Model.Serializer.writeObjectProtoBuf(root, rootCache, { rootBytes[offset++] = it }, context)
    val dependencies = mutableListOf<MarykPrimitive>()
    dataModel.getAllDependencies(dependencies)
    val dependencyBytes = if (dependencies.isEmpty()) byteArrayOf() else {
        val definitions = Definitions(dependencies.sortedWith(compareBy({ it.Meta.primitiveType.index }, { it.Meta.name })))
        val cache = WriteCache()
        val size = Definitions.Serializer.calculateObjectProtoBufLength(definitions, cache, context)
        ByteArray(size).also { bytes ->
            offset = 0
            Definitions.Serializer.writeObjectProtoBuf(definitions, cache, { bytes[offset++] = it }, context)
        }
    }
    // Frame both fields so moving bytes between the root and its dependencies cannot alias.
    val bytes = "maryk.migration.target/v1\u0000".encodeToByteArray() +
        rootBytes.size.frameSize() + rootBytes + dependencyBytes.size.frameSize() + dependencyBytes
    return CryptographyProvider.Default.get(SHA256).hasher().hash(bytes)
}

suspend fun requireMigrationTargetIdentity(storedIdentity: ByteArray?, dataModel: IsRootDataModel) {
    if (storedIdentity == null) {
        throw MigrationException(
            "Pending migration for ${dataModel.Meta.name} has no target schema identity; " +
                "recover its authoritative target schema and migration progress before resuming"
        )
    }
    if (!storedIdentity.contentEquals(migrationTargetIdentity(dataModel))) {
        throw MigrationException("Pending migration target schema changed for ${dataModel.Meta.name}; reopen with its original target schema")
    }
}

private fun Int.frameSize() = byteArrayOf(
    (this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte(),
)
