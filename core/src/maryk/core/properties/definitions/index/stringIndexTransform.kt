package maryk.core.properties.definitions.index

import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference

data class StringIndexTransform(
    val normalize: Boolean = false,
    val splitOn: SplitOn? = null
) {
    fun apply(value: String): Any {
        return splitOn?.let { split ->
            splitStringForIndex(value, split).map { if (normalize) normalizeStringForIndex(it) else it }
        } ?: if (normalize) normalizeStringForIndex(value) else value
    }
}

fun IsIndexable.stringIndexTransform(reference: AnyPropertyReference): StringIndexTransform? = when (this) {
    is Normalize -> if (isForPropertyReference(reference)) {
        this.reference.stringIndexTransform(reference)?.copy(normalize = true) ?: StringIndexTransform(normalize = true)
    } else null
    is Split -> if (isForPropertyReference(reference)) {
        this.reference.stringIndexTransform(reference)?.copy(splitOn = on) ?: StringIndexTransform(splitOn = on)
    } else null
    is AnyOf -> this.references.firstNotNullOfOrNull { it.stringIndexTransform(reference) }
    is Multiple -> this.references.firstNotNullOfOrNull { it.stringIndexTransform(reference) }
    is IsIndexablePropertyReference<*> -> if (isForPropertyReference(reference)) StringIndexTransform() else null
    else -> null
}
