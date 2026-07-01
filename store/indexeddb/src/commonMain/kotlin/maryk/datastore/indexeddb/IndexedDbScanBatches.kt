package maryk.datastore.indexeddb

private const val ScanBatchSize = 256u

internal suspend fun IndexedDbByteStore.scanInBatches(
    storeName: String,
    startKey: ByteArray? = null,
    includeStart: Boolean = true,
    endKey: ByteArray? = null,
    includeEnd: Boolean = true,
    reverse: Boolean = false,
    targetLimit: UInt,
    process: suspend (key: ByteArray, value: ByteArray) -> Boolean,
) {
    if (targetLimit == 0u) return

    var nextStartKey = startKey
    var nextIncludeStart = includeStart
    var nextEndKey = endKey
    var nextIncludeEnd = includeEnd
    val pageLimit = minOf(targetLimit, ScanBatchSize)
    var processed = 0u

    while (true) {
        val rows = scan(
            storeName = storeName,
            startKey = nextStartKey,
            includeStart = nextIncludeStart,
            endKey = nextEndKey,
            includeEnd = nextIncludeEnd,
            reverse = reverse,
            limit = pageLimit,
        )
        if (rows.isEmpty()) return

        for ((key, value) in rows) {
            if (!process(key, value)) return
            processed++
            if (processed >= targetLimit) return
        }

        if (rows.size.toUInt() < pageLimit) return
        val lastKey = rows.last().first
        if (reverse) {
            nextEndKey = lastKey
            nextIncludeEnd = false
        } else {
            nextStartKey = lastKey
            nextIncludeStart = false
        }
    }
}
