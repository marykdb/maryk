package maryk.lib

/** Multi-platform version for a native freeze, so it can be called from common code */
expect fun <T> T.freeze(): T
