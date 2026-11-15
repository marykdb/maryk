package maryk.datastore.rocksdb

import maryk.rocksdb.AbstractComparator
import maryk.rocksdb.ComparatorOptions

internal expect fun createPlatformVersionedComparator(options: ComparatorOptions, keySize: Int): AbstractComparator
