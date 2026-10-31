package maryk.core.models.serializers

private val localContinuations = object : ThreadLocal<MutableList<ProtoBufContinuation>>() {
    override fun initialValue() = mutableListOf<ProtoBufContinuation>()
}

internal actual val protoBufContinuations: MutableList<ProtoBufContinuation>
    get() = checkNotNull(localContinuations.get())
