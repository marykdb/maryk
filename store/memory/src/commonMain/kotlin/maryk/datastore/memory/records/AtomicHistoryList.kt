package maryk.datastore.memory.records

/** Index history entries are immutable; copy their list only when a staged mutation edits it. */
internal class AtomicHistoryList<T>(
    private val source: MutableList<T>,
    private val budget: (Int) -> Unit,
) : AbstractMutableList<T>() {
    private var changed: MutableList<T>? = null
    override val size get() = changed?.size ?: source.size
    override fun get(index: Int): T = changed?.get(index) ?: source[index]
    private fun writable(): MutableList<T> = changed ?: run {
        budget(source.size)
        source.toMutableList().also { changed = it }
    }
    override fun add(index: Int, element: T) = writable().add(index, element)
    override fun removeAt(index: Int): T = writable().removeAt(index)
    override fun set(index: Int, element: T): T = writable().set(index, element)
    fun committed(): MutableList<T> = changed ?: source
}
