package maryk.core.processors.datastore.scanRange

internal fun multiplyList(start: MutableList<MutableList<Byte>>, end: MutableList<MutableList<Byte>>, size: Int) {
    fun multiplyListInternal(list: MutableList<MutableList<Byte>>) {
        val originalSize = list.size
        repeat(size - 1) {
            for (i in 0 until originalSize) {
                list.add(ArrayList(list[i]))
            }
        }
    }

    multiplyListInternal(start)
    multiplyListInternal(end)
}