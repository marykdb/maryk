package maryk.datastore.foundationdb.processors.helpers

import com.apple.foundationdb.directory.DirectorySubspace
import com.apple.foundationdb.tuple.Tuple

internal fun packKey(directory: DirectorySubspace, vararg segments: ByteArray): ByteArray {
    val tuple = Tuple()
    for (s in segments) tuple.add(s)
    return directory.pack(tuple)
}
