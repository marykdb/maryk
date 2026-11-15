package maryk.datastore.rocksdb

import maryk.datastore.rocksdb.processors.VersionedComparator
import maryk.rocksdb.AbstractComparator
import maryk.rocksdb.ComparatorOptions

internal actual fun createPlatformVersionedComparator(options: ComparatorOptions, keySize: Int): AbstractComparator = VersionedComparator(options, keySize)
