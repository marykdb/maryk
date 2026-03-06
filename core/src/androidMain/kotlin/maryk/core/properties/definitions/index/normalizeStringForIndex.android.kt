package maryk.core.properties.definitions.index

import java.text.Normalizer

internal actual fun decomposeUnicodeForIndex(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
