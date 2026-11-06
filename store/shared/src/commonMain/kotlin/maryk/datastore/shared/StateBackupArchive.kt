package maryk.datastore.shared

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key

private val StateArchiveMagic = "MARYK-STATE-2\n".encodeToByteArray()
private const val MaximumArchiveFrameBytes = 2 * MAX_ATOMIC_BYTES
private const val ManifestFrame = 1
private const val RecordsFrame = 2
private const val CompleteFrame = 3

/**
 * Bounded binary state archive with chained SHA-256 checksums and a required completion frame.
 * [writeBytes] appends to private output. [publish] must durably finish and atomically publish it.
 * Supply encryption/compression through the transport when needed. Checksums do not authenticate
 * an archive against deliberate replacement. A failed writer is single-use; discard its output.
 */
class StateBackupArchiveWriter(
    dataModelsById: Map<UInt, IsRootDataModel>,
    private val writeBytes: suspend (ByteArray) -> Unit,
    private val publish: suspend () -> Unit,
) : DataStoreStateBackupWriter {
    private val models = dataModelsById.values.associateBy { it.Meta.name }
    private val codec = StateBackupCodec(models)
    private var started = false
    private var finished = false
    private var failed = false
    private var checksum = ByteArray(32)
    private var declaredModels = emptySet<String>()

    private suspend fun frame(type: Int, payload: ByteArray) {
        if (payload.size > MaximumArchiveFrameBytes) throw RequestException("State archive frame exceeds byte limit")
        val header = archiveInt(type) + archiveInt(payload.size)
        val next = CryptographyProvider.Default.get(SHA256).hasher().hash(checksum + header + payload)
        try {
            writeBytes(header + payload + next)
            checksum = next
        } catch (error: Throwable) {
            failed = true
            throw error
        }
    }

    override suspend fun begin(manifest: DataStoreStateBackupManifest) {
        check(!started && !failed) { "Archive writer is single-use" }
        started = true
        try {
            if (manifest.modelNames.size > 10_000 || manifest.formatVersion != DATA_STORE_STATE_BACKUP_FORMAT_VERSION ||
                manifest.modelNames.distinct().size != manifest.modelNames.size ||
                manifest.modelMajorVersions.keys != manifest.modelNames.toSet()
            ) throw RequestException("Invalid state archive manifest")
            declaredModels = manifest.modelNames.toSet()
            val fields = mutableListOf(archiveInt(manifest.formatVersion.toInt()), archiveInt(manifest.modelNames.size))
            for (name in manifest.modelNames) {
                if (models[name]?.Meta?.version?.major != manifest.modelMajorVersions.getValue(name)) throw RequestException("State archive model `$name` is not registered at its required version")
                fields += archiveBytes(name.encodeToByteArray())
                fields += archiveInt(manifest.modelMajorVersions.getValue(name).toInt())
            }
            val payload = joinArchiveFields(fields)
            writeBytes(StateArchiveMagic.copyOf())
            checksum = CryptographyProvider.Default.get(SHA256).hasher().hash(StateArchiveMagic)
            frame(ManifestFrame, payload)
        } catch (error: Throwable) { failed = true; throw error }
    }

    override suspend fun write(chunk: DataStoreStateBackupChunk) {
        check(started && !finished && !failed) { "Archive writer is not active" }
        try {
            if (chunk.modelName !in declaredModels || chunk.records.size > 1000) throw RequestException("Invalid state archive chunk")
            val fields = mutableListOf(archiveBytes(chunk.modelName.encodeToByteArray()), archiveInt(chunk.records.size))
            if (chunk.allocations.keys.any { key -> chunk.records.none { it.key == key } }) throw RequestException("State allocations refer to a missing record")
            var remaining = MAX_ATOMIC_BYTES
            for (record in chunk.records) {
                validateStateRecord(record)
                val allocations = snapshotStateAllocations(models.getValue(chunk.modelName), chunk.allocations[record.key].orEmpty())
                remaining -= stateAllocationBytes(allocations)
                val bytes = codec.encode(chunk.modelName, record, remaining)
                remaining -= bytes.size
                fields += archiveBytes(bytes)
                fields += archiveInt(allocations.size)
                for ((reference, maximum) in allocations.entries.sortedBy { it.key }) {
                    fields += archiveBytes(reference.bytes)
                    fields += archiveBytes(maximum.bytes)
                }
            }
            frame(RecordsFrame, joinArchiveFields(fields))
        } catch (error: Throwable) { failed = true; throw error }
    }

    override suspend fun complete() {
        check(started && !finished && !failed) { "Archive writer is not active" }
        try {
            frame(CompleteFrame, byteArrayOf())
            publish()
            finished = true
        } catch (error: Throwable) { failed = true; throw error }
    }
}

/**
 * Opens one finite archive. [readBytes] returns up to the requested byte count, or empty at EOF.
 * Reads are bounded even for malformed lengths. The reader is consumed once and requires both
 * its completion frame and EOF. A late checksum failure can leave an isolated restore partial.
 */
suspend fun openStateBackupArchive(
    dataModelsById: Map<UInt, IsRootDataModel>,
    readBytes: suspend (Int) -> ByteArray,
): DataStoreStateBackupReader {
    val input = StateArchiveInput(readBytes)
    val magic = input.exact(StateArchiveMagic.size)
    if (!magic.contentEquals(StateArchiveMagic)) throw RequestException("Invalid state archive signature")
    input.checksum = CryptographyProvider.Default.get(SHA256).hasher().hash(magic)
    val (type, payload) = input.frame()
    if (type != ManifestFrame) throw RequestException("State archive is missing its manifest")
    val fields = ArchiveFields(payload)
    val version = fields.int()
    if (version.toUInt() != DATA_STORE_STATE_BACKUP_FORMAT_VERSION) throw RequestException("Unsupported state archive format $version")
    val count = fields.int()
    if (count !in 0..10_000) throw RequestException("Invalid state archive model count")
    val names = mutableListOf<String>()
    val versions = mutableMapOf<String, UShort>()
    repeat(count) {
        val name = fields.bytes().decodeToString(throwOnInvalidSequence = true)
        val major = fields.int()
        if (name.isEmpty() || name in versions || major !in 0..65535) throw RequestException("Invalid state archive model definition")
        names += name
        versions[name] = major.toUShort()
    }
    fields.end()
    val manifest = DataStoreStateBackupManifest(names, versions, version.toUInt())
    val models = dataModelsById.values.associateBy { it.Meta.name }
    for (name in names) {
        val model = models[name] ?: throw RequestException("State archive model `$name` is not registered")
        if (model.Meta.version.major != versions.getValue(name)) {
            throw RequestException("State archive model `$name` has an incompatible major version")
        }
    }
    val codec = StateBackupCodec(models)
    return object : DataStoreStateBackupReader {
        override val manifest = manifest
        private var consumed = false
        override suspend fun read(consumer: suspend (DataStoreStateBackupChunk) -> Unit) {
            check(!consumed) { "State archive reader is single-use" }
            consumed = true
            while (true) {
                val (frameType, bytes) = input.frame()
                if (frameType == CompleteFrame) {
                    if (bytes.isNotEmpty() || readBytes(1).isNotEmpty()) throw RequestException("State archive has trailing content")
                    return
                }
                if (frameType != RecordsFrame) throw RequestException("Unexpected state archive frame $frameType")
                val recordFields = ArchiveFields(bytes)
                val name = recordFields.bytes().decodeToString(throwOnInvalidSequence = true)
                if (name !in versions) throw RequestException("Undeclared state archive model `$name`")
                val records = recordFields.int()
                if (records !in 0..1000) throw RequestException("Invalid state archive record count")
                var remaining = MAX_ATOMIC_BYTES
                val allocations = mutableMapOf<Key<IsRootDataModel>, StateBackupAllocations>()
                val values = List(records) {
                    val record = recordFields.bytes()
                    if (record.size > remaining) throw RequestException("State archive chunk exceeds byte limit")
                    remaining -= record.size
                    val value = codec.decode(name, record)
                    val count = recordFields.int()
                    if (count < 0 || count > remaining / 8) throw RequestException("Invalid state allocation count")
                    val collector = StateBackupAllocationCollector(models.getValue(name))
                    val seen = mutableSetOf<Bytes>()
                    repeat(count) {
                        val reference = recordFields.bytes()
                        val maximum = recordFields.bytes()
                        if (!seen.add(Bytes(reference))) throw RequestException("Duplicate state allocation reference")
                        collector.add(reference, maximum)
                    }
                    val maxima = collector.snapshot()
                    remaining -= stateAllocationBytes(maxima)
                    if (remaining < 0) throw RequestException("State archive chunk exceeds byte limit")
                    allocations[value.key] = maxima
                    value
                }
                recordFields.end()
                consumer(DataStoreStateBackupChunk(name, values, allocations))
            }
        }
    }
}

private class StateArchiveInput(private val read: suspend (Int) -> ByteArray) {
    var checksum = ByteArray(32)
    suspend fun exact(size: Int): ByteArray {
        val result = ByteArray(size)
        var position = 0
        while (position < size) {
            val bytes = read(size - position)
            if (bytes.isEmpty() || bytes.size > size - position) throw RequestException("Truncated or invalid state archive stream")
            bytes.copyInto(result, position)
            position += bytes.size
        }
        return result
    }
    suspend fun frame(): Pair<Int, ByteArray> {
        val header = exact(8)
        val fields = ArchiveFields(header)
        val type = fields.int()
        val length = fields.int()
        if (length !in 0..MaximumArchiveFrameBytes) throw RequestException("State archive frame length is out of bounds")
        val payload = exact(length)
        val expected = exact(32)
        val actual = CryptographyProvider.Default.get(SHA256).hasher().hash(checksum + header + payload)
        if (!actual.contentEquals(expected)) throw RequestException("State archive checksum mismatch")
        checksum = actual
        return type to payload
    }
}

private fun archiveInt(value: Int): ByteArray = ByteArray(4) { (value ushr (24 - it * 8)).toByte() }
private fun archiveBytes(bytes: ByteArray) = archiveInt(bytes.size) + bytes
private fun joinArchiveFields(fields: List<ByteArray>): ByteArray {
    val size = fields.sumOf { it.size.toLong() }
    if (size > MaximumArchiveFrameBytes) throw RequestException("State archive frame exceeds byte limit")
    var offset = 0
    return ByteArray(size.toInt()).also { result -> fields.forEach { it.copyInto(result, offset); offset += it.size } }
}
private class ArchiveFields(private val bytes: ByteArray) {
    private var offset = 0
    fun int(): Int {
        if (bytes.size - offset < 4) throw RequestException("Truncated state archive field")
        var value = 0
        repeat(4) { value = (value shl 8) or (bytes[offset++].toInt() and 255) }
        return value
    }
    fun bytes(): ByteArray {
        val length = int()
        if (length < 0 || length > bytes.size - offset) throw RequestException("Invalid state archive field length")
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }
    fun end() {
        if (offset != bytes.size) throw RequestException("Unexpected state archive fields")
    }
}
