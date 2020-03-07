package maryk.lib

import kotlin.native.concurrent.freeze

actual fun <T> T.freeze() = this.freeze()
