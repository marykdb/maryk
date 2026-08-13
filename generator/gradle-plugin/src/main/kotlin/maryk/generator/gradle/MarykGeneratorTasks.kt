package maryk.generator.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.Comparator
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import maryk.generator.build.SchemaBuildEngine
import maryk.generator.build.SchemaBuildException
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@CacheableTask
abstract class MarykGenerateModelsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val generatorVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @TaskAction
    fun generate() {
        val projectDirectory = projectDirectory.get().asFile.toPath()
        SchemaBuildEngine.generate(
            schemaFiles = SchemaBuildEngine.discoverSchemas(schemas.files.map { it.toPath() }),
            packageName = packageName.get(),
            outputDirectory = outputDirectory.get().asFile.toPath(),
            projectDirectory = projectDirectory,
            sourceDirectories = listOf(projectDirectory.resolve("src")) +
                sourceDirectories.files.map { it.toPath() },
        )
    }
}

@CacheableTask
abstract class MarykCheckSchemaCompatibilityTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselines: ConfigurableFileCollection

    @get:Input
    abstract val allowRemovedModels: Property<Boolean>

    @get:Input
    abstract val compatibilityFormatVersion: Property<Int>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun check() {
        val report = SchemaBuildEngine.checkCompatibility(
            currentSchemaFiles = SchemaBuildEngine.discoverSchemas(schemas.files.map { it.toPath() }),
            baselineSchemaFiles = SchemaBuildEngine.discoverSchemas(baselines.files.map { it.toPath() }),
            allowRemovedModels = allowRemovedModels.get(),
        )
        val text = if (report.isCompatible) {
            "Maryk schemas are compatible\n"
        } else {
            report.reasons.joinToString(
                prefix = "Maryk schema incompatibilities:\n",
                separator = "\n",
                postfix = "\n",
            )
        }
        val path = reportFile.get().asFile.toPath()
        path.parent.createDirectories()
        path.writeText(text)
        if (!report.isCompatible) throw SchemaBuildException(text.trimEnd())
    }
}

@DisableCachingByDefault(because = "Explicitly mutates the source-controlled schema baseline")
abstract class MarykUpdateSchemaBaselineTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemas: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val baselineDirectory: DirectoryProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun update() {
        val schemaRoots = schemas.files.map { it.toPath().toAbsolutePath().normalize() }
        val project = projectDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val output = baselineDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        validateBaselineDirectory(output, project, schemaRoots)
        val schemaFiles = SchemaBuildEngine.discoverSchemas(schemaRoots)
        SchemaBuildEngine.load(schemaFiles)
        val outputParent = requireNotNull(output.parent) { "Maryk baseline directory must have a parent" }
        outputParent.createDirectories()
        val staged = Files.createTempDirectory(outputParent, ".${output.fileName}-")
        try {
            schemaFiles.forEach { source ->
                val destination = staged.resolve(source.relativeToSchemaRoot(schemaRoots))
                if (Files.exists(destination)) {
                    throw SchemaBuildException("Maryk schemas have conflicting relative path ${staged.relativize(destination)}")
                }
                destination.parent.createDirectories()
                Files.copy(source, destination)
            }
            staged.resolve(BASELINE_MARKER).writeText(BASELINE_MARKER_CONTENT)
            replaceDirectory(output, staged)
        } finally {
            if (Files.exists(staged)) deleteDirectory(staged)
        }
    }

    private fun validateBaselineDirectory(output: Path, project: Path, schemaRoots: List<Path>) {
        require(output.startsWith(project)) {
            "Maryk baseline directory must be inside the project directory"
        }
        require(schemaRoots.none(::hasSymbolicLink)) {
            "Maryk schema source directories cannot use symbolic links when updating a baseline"
        }
        require(schemaRoots.none { root -> output.startsWith(root) || root.startsWith(output) }) {
            "Maryk baseline directory cannot overlap a schema source directory"
        }
        var current = project
        project.relativize(output).forEach { segment ->
            current = current.resolve(segment)
            require(!Files.isSymbolicLink(current)) {
                "Maryk baseline directory cannot use symbolic links"
            }
        }
        if (Files.exists(output)) {
            require(Files.isDirectory(output)) {
                "Maryk baseline directory is not a managed baseline"
            }
            val marker = output.resolve(BASELINE_MARKER)
            if (Files.exists(marker)) {
                require(Files.readString(marker) == BASELINE_MARKER_CONTENT) {
                    "Maryk baseline directory is not a managed baseline"
                }
            } else {
                Files.list(output).use { entries ->
                    require(!entries.findAny().isPresent) {
                        "Maryk baseline directory is not a managed baseline"
                    }
                }
            }
        }
    }

    private fun hasSymbolicLink(path: Path): Boolean {
        var current = path.root ?: return false
        path.forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) return true
        }
        return false
    }

    private fun Path.relativeToSchemaRoot(schemaRoots: List<Path>): Path {
        val absoluteSource = toAbsolutePath().normalize()
        val root = schemaRoots
            .filter { absoluteSource.startsWith(it) }
            .maxByOrNull { it.nameCount }
            ?: throw SchemaBuildException("Could not determine schema root for $absoluteSource")
        return if (Files.isDirectory(root)) root.relativize(absoluteSource) else absoluteSource.fileName
    }

    private fun replaceDirectory(output: Path, staged: Path) {
        val backup = output.resolveSibling(".${output.fileName}-backup-${System.nanoTime()}")
        var movedOutput = false
        try {
            if (Files.exists(output)) {
                Files.move(output, backup, ATOMIC_MOVE)
                movedOutput = true
            }
            Files.move(staged, output, ATOMIC_MOVE)
        } catch (failure: Exception) {
            if (movedOutput && Files.notExists(output)) {
                Files.move(backup, output, ATOMIC_MOVE)
            }
            throw failure
        }
        if (movedOutput) deleteDirectory(backup)
    }

    private fun deleteDirectory(directory: Path) {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        const val BASELINE_MARKER = ".maryk-schema-baseline"
        const val BASELINE_MARKER_CONTENT = "Managed by the Maryk schema baseline task."
    }
}
