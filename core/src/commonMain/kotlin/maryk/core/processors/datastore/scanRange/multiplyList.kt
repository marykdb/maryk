package maryk.core.processors.datastore.scanRange

internal fun multiplyList(start: MutableList<MutableList<Byte>>, end: MutableList<MutableList<Byte>>, size: Int) {
    val startCopy = start.toList()
    val endCopy = end.toList()

    for (byteList in startCopy) {
        for (it in 1 until size) { // first one is already there
            start.add(byteList.toMutableList())
        }
    }
    for (byteList in endCopy) {
        for (it in 1 until size) { // first one is already there
            end.add(byteList.toMutableList())
        }
    }
}
