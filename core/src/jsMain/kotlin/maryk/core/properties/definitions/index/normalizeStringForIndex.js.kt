package maryk.core.properties.definitions.index

@Suppress("UnsafeCastFromDynamic")
internal actual fun decomposeUnicodeForIndex(value: String): String =
    js("value.normalize('NFD')") as String
