package maryk.datastore.rocksdb

import maryk.rocksdb.CompressionType
import maryk.rocksdb.IndexSearchType
import maryk.rocksdb.PersistentCache

/**
 * Optional SST tuning. Defaults work without caller-owned resources or extra files.
 * Existing SSTs remain readable; filter/compression changes apply to newly written SSTs.
 *
 * @property bloomFilterBitsPerKey Bits per key for new SST Bloom filters; null disables them.
 * @property indexSearchType Search strategy for bytewise families. Historic families always use binary search.
 * @property compression General SST codec; existing files retain their original codec until rewritten.
 * @property bottommostCompression Codec for bottommost compactions, including with per-level compression.
 * @property compressionPerLevel Optional codecs by LSM level; empty uses [compression]. Copied at open.
 * @property persistentCache Optional caller-owned SST page cache. Keep it and its env/logger alive until the store closes.
 */
data class RocksDBTableConfiguration(
    val bloomFilterBitsPerKey: Double? = 10.0,
    val indexSearchType: IndexSearchType = IndexSearchType.kAuto,
    val compression: CompressionType = CompressionType.LZ4_COMPRESSION,
    val bottommostCompression: CompressionType = CompressionType.ZSTD_COMPRESSION,
    val compressionPerLevel: List<CompressionType> = emptyList(),
    val persistentCache: PersistentCache? = null,
) {
    init {
        require(bloomFilterBitsPerKey == null || (bloomFilterBitsPerKey.isFinite() && bloomFilterBitsPerKey > 0.0)) {
            "Bloom filter bits per key must be positive and finite, or null to disable filters"
        }
    }
}
