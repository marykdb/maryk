package maryk.core.properties.definitions.index

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference

fun IsRootDataModel.namedSearchIndex(name: String) =
    this.Meta.indexes
        ?.filterIsInstance<AnyOf>()
        ?.firstOrNull { it.name == name }

internal fun AnyOf.queryToStorageByteArrays(value: String): List<ByteArray> =
    references.flatMap { reference ->
        reference.toQueryStrings(value).map { queryValue ->
            ByteArray(reference.calculateStorageByteLength(queryValue)).also { byteArray ->
                var writeIndex = 0
                reference.writeStorageBytes(queryValue) { byteArray[writeIndex++] = it }
            }
        }
    }.distinct()

internal fun IsIndexablePropertyReference<String>.toQueryStrings(value: String): List<String> =
    when (val transformed = this.directStringIndexTransform().apply(value)) {
        is Collection<*> -> transformed.filterIsInstance<String>()
        is String -> listOf(transformed)
        else -> emptyList()
    }

fun IsRootDataModel.matchesNamedSearchIndex(
    name: String,
    value: String,
    valueMatcher: (IsPropertyReference<*, *, *>, (Any?) -> Boolean) -> Boolean
): Boolean {
    val searchIndex = namedSearchIndex(name) ?: return false
    val expectedValues = searchIndex.references.flatMap { it.toQueryStrings(value) }.distinct()
    if (expectedValues.isEmpty()) return false

    return expectedValues.all { expectedValue ->
        searchIndex.references.any { reference ->
            valueMatcher(reference.toPropertyReference()) { actualValue ->
                when (val transformed = reference.directStringIndexTransform().apply(actualValue as? String ?: return@valueMatcher false)) {
                    is Collection<*> -> transformed.any { it == expectedValue }
                    is String -> transformed == expectedValue
                    else -> false
                }
            }
        }
    }
}

fun IsRootDataModel.matchesNamedSearchIndexPrefix(
    name: String,
    value: String,
    valueMatcher: (IsPropertyReference<*, *, *>, (Any?) -> Boolean) -> Boolean
): Boolean {
    val searchIndex = namedSearchIndex(name) ?: return false
    val expectedPrefixes = searchIndex.references.flatMap { it.toQueryStrings(value) }.distinct()
    if (expectedPrefixes.isEmpty()) return false

    return expectedPrefixes.all { expectedPrefix ->
        searchIndex.references.any { reference ->
            valueMatcher(reference.toPropertyReference()) { actualValue ->
                when (val transformed = reference.directStringIndexTransform().apply(actualValue as? String ?: return@valueMatcher false)) {
                    is Collection<*> -> transformed.any { it is String && it.startsWith(expectedPrefix) }
                    is String -> transformed.startsWith(expectedPrefix)
                    else -> false
                }
            }
        }
    }
}

fun IsRootDataModel.matchesNamedSearchIndexRegex(
    name: String,
    regex: Regex,
    valueMatcher: (IsPropertyReference<*, *, *>, (Any?) -> Boolean) -> Boolean
): Boolean {
    val searchIndex = namedSearchIndex(name) ?: return false

    return searchIndex.references.any { reference ->
        valueMatcher(reference.toPropertyReference()) { actualValue ->
            when (val transformed = reference.directStringIndexTransform().apply(actualValue as? String ?: return@valueMatcher false)) {
                is Collection<*> -> transformed.any { it is String && regex.matches(it) }
                is String -> regex.matches(transformed)
                else -> false
            }
        }
    }
}

private fun IsIndexablePropertyReference<String>.directStringIndexTransform(): StringIndexTransform = when (this) {
    is Normalize -> this.reference.directStringIndexTransform().copy(normalize = true)
    is Split -> this.reference.directStringIndexTransform().copy(splitOn = on)
    else -> StringIndexTransform()
}

private fun IsIndexablePropertyReference<String>.toPropertyReference(): IsPropertyReference<*, *, *> = when (this) {
    is Normalize -> this.reference.toPropertyReference()
    is Split -> this.reference.toPropertyReference()
    is IsPropertyReference<*, *, *> -> this
    else -> throw IllegalArgumentException("Search index reference $this cannot be resolved to a property reference")
}
