@file:OptIn(ExperimentalNativeApi::class)

package maryk.datastore.rocksdb

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import maryk.rocksdb.Cache
import maryk.rocksdb.LRUCache

internal actual fun createPlatformBlockCache(): Cache? =
    if (Platform.osFamily == OsFamily.WINDOWS) {
        LRUCache(WINDOWS_BLOCK_CACHE_CAPACITY)
    } else {
        null
    }

private const val WINDOWS_BLOCK_CACHE_CAPACITY = 32L * 1024 * 1024
