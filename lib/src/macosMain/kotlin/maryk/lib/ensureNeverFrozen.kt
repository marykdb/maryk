package maryk.lib

import kotlin.native.concurrent.ensureNeverFrozen

actual fun Any.ensureNeverFrozen() = this.ensureNeverFrozen()
