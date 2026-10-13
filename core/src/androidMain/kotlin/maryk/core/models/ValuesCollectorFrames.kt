package maryk.core.models

private val localFrames = object : ThreadLocal<MutableList<ValuesCollectorFrame>>() {
    override fun initialValue() = mutableListOf<ValuesCollectorFrame>()
}

internal actual val valuesCollectorFrames: MutableList<ValuesCollectorFrame>
    // Android annotates get() as nullable; initialValue() always supplies a frame stack.
    get() = checkNotNull(localFrames.get())
