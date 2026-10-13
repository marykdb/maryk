package maryk.core.models

private val localFrames = object : ThreadLocal<MutableList<ValuesCollectorFrame>>() {
    override fun initialValue() = mutableListOf<ValuesCollectorFrame>()
}

internal actual val valuesCollectorFrames: MutableList<ValuesCollectorFrame>
    get() = localFrames.get()
