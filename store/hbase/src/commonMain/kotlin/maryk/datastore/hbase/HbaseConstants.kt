package maryk.datastore.hbase

val dataColumnFamily = "d".encodeToByteArray()
val metaColumnFamily = "m".encodeToByteArray()

val softDeleteIndicator: ByteArray = byteArrayOf(0)
val trueIndicator: ByteArray = byteArrayOf(1)
