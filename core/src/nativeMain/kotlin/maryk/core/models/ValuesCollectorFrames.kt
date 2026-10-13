package maryk.core.models

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
internal actual val valuesCollectorFrames = mutableListOf<ValuesCollectorFrame>()
