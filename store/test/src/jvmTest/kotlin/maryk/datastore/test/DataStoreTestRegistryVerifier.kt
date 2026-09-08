package maryk.datastore.test

import java.io.File

private val dataStoreTestClassPattern = Regex(
    """class\s+(\w+)[^{]*\bIsDataStoreTest\b[^{]*\{"""
)
private val registeredDataStoreTestClassPattern = Regex(""""[^"]+"\s+to\s+::(\w+)""")

internal fun findUnregisteredDataStoreTestClasses(
    testSources: Iterable<File>,
    registrySource: String,
): Set<String> {
    val registeredClasses = registeredDataStoreTestClassPattern.findAll(registrySource)
        .map { it.groupValues[1] }
        .toSet()
    val declaredClasses = testSources.flatMap { source ->
        dataStoreTestClassPattern.findAll(source.readText()).map { it.groupValues[1] }
    }.toSet()

    return declaredClasses - registeredClasses
}
