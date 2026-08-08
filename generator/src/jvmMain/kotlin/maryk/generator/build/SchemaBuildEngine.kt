package maryk.generator.build

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.Comparator
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import maryk.core.models.IsRootDataModel
import maryk.core.models.migration.MigrationStatus
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.yaml.MarykYamlModelReader
import maryk.generator.kotlin.generateKotlin
import maryk.json.JsonReader

class SchemaBuildException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class GenerationResult(
    val files: List<Path>,
)

data class CompatibilityReport(
    val reasons: List<String>,
) {
    val isCompatible: Boolean
        get() = reasons.isEmpty()
}

data class LoadedSchema(
    val path: Path,
    val model: IsRootDataModel,
)

object SchemaBuildEngine {
    private val supportedExtensions = setOf("yaml", "yml", "json")

    fun discoverSchemas(roots: Iterable<Path>): List<Path> =
        roots.flatMap { root ->
            when {
                Files.notExists(root) -> emptyList()
                root.isDirectory() -> Files.walk(root).use { paths ->
                    paths.filter(Files::isRegularFile)
                        .filter(::isSchema)
                        .toList()
                }
                isSchema(root) -> listOf(root)
                else -> emptyList()
            }
        }.distinct()
            .sortedBy { it.toAbsolutePath().normalize().toString() }

    fun load(schemaFiles: List<Path>): List<LoadedSchema> {
        if (schemaFiles.isEmpty()) {
            throw SchemaBuildException("No Maryk schemas found; configure at least one .yaml, .yml, or .json schema")
        }

        val context = DefinitionsConversionContext()
        val loaded = schemaFiles.sortedBy { it.toAbsolutePath().normalize().toString() }.map { path ->
            val model = try {
                val reader = if (path.extension.lowercase() == "json") {
                    JsonReader(path.readText())
                } else {
                    MarykYamlModelReader(path.readText())
                }
                maryk.core.models.RootDataModel.Model.Serializer.readJson(reader, context).toDataObject()
            } catch (throwable: Throwable) {
                throw SchemaBuildException("Could not parse Maryk schema ${path.toAbsolutePath()}: ${throwable.message}", throwable)
            }
            context.dataModels[model.Meta.name] = DataModelReference(model)
            LoadedSchema(path, model)
        }

        loaded.groupBy { it.model.Meta.name }
            .filterValues { it.size > 1 }
            .toSortedMap()
            .entries
            .firstOrNull()
            ?.let { (name, duplicates) ->
                throw SchemaBuildException(
                    "Duplicate model name $name in ${duplicates.joinToString { it.path.toAbsolutePath().toString() }}",
                )
            }
        return loaded
    }

    fun generate(
        schemaFiles: List<Path>,
        packageName: String,
        outputDirectory: Path,
    ): GenerationResult {
        require(packageName.isNotBlank()) { "Maryk generator packageName must not be blank" }
        val loaded = load(schemaFiles)
        val generated = loaded.associate { schema ->
            var source = ""
            schema.model.generateKotlin(packageName) { source = it }
            "${schema.model.Meta.name}.kt" to source
        }.toSortedMap()

        val absoluteOutputDirectory = outputDirectory.toAbsolutePath().normalize()
        val parentDirectory = absoluteOutputDirectory.parent
            ?: throw SchemaBuildException("Maryk generator output directory must have a parent")
        Files.createDirectories(parentDirectory)
        val stagingDirectory = Files.createTempDirectory(parentDirectory, ".maryk-generator-")
        try {
            generated.forEach { (name, source) ->
                stagingDirectory.resolve(name).writeText(source)
            }
            replaceManagedDirectory(stagingDirectory, absoluteOutputDirectory)
        } finally {
            clearManagedDirectory(stagingDirectory)
            Files.deleteIfExists(stagingDirectory)
        }
        return GenerationResult(generated.keys.map(outputDirectory::resolve))
    }

    fun checkCompatibility(
        currentSchemaFiles: List<Path>,
        baselineSchemaFiles: List<Path>,
        allowRemovedModels: Boolean = false,
    ): CompatibilityReport {
        val current = load(currentSchemaFiles).associateBy { it.model.Meta.name }
        val baseline = if (baselineSchemaFiles.isEmpty()) {
            emptyMap()
        } else {
            load(baselineSchemaFiles).associateBy { it.model.Meta.name }
        }
        val reasons = mutableListOf<String>()

        baseline.toSortedMap().forEach { (name, stored) ->
            val candidate = current[name]
            if (candidate == null) {
                if (!allowRemovedModels) {
                    reasons += "$name: baseline model is missing from current schemas"
                }
                return@forEach
            }
            val status = candidate.model.isMigrationNeeded(stored.model)
            if (status is MigrationStatus.NeedsMigration) {
                status.migrationReasons.sorted().forEach { reason ->
                    reasons += "$name: $reason"
                }
            }
        }
        return CompatibilityReport(reasons)
    }

    private fun isSchema(path: Path): Boolean =
        path.extension.lowercase() in supportedExtensions

    private fun clearManagedDirectory(directory: Path) {
        if (Files.notExists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path != directory) Files.deleteIfExists(path)
            }
        }
    }

    private fun replaceManagedDirectory(stagingDirectory: Path, outputDirectory: Path) {
        if (Files.notExists(outputDirectory)) {
            Files.move(stagingDirectory, outputDirectory, ATOMIC_MOVE)
            return
        }

        val backupDirectory = Files.createTempDirectory(outputDirectory.parent, ".maryk-generator-backup-")
        Files.delete(backupDirectory)
        var hasBackup = false
        try {
            Files.move(outputDirectory, backupDirectory, ATOMIC_MOVE)
            hasBackup = true
            Files.move(stagingDirectory, outputDirectory, ATOMIC_MOVE)
            clearManagedDirectory(backupDirectory)
            Files.deleteIfExists(backupDirectory)
        } catch (throwable: Throwable) {
            if (hasBackup && Files.notExists(outputDirectory)) {
                Files.move(backupDirectory, outputDirectory, ATOMIC_MOVE)
                hasBackup = false
            }
            throw throwable
        } finally {
            if (hasBackup && Files.exists(backupDirectory)) {
                clearManagedDirectory(backupDirectory)
                Files.deleteIfExists(backupDirectory)
            }
        }
    }
}
