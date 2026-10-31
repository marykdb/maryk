package maryk.core.models.serializers

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual val protoBufContinuations = mutableListOf<ProtoBufContinuation>()
