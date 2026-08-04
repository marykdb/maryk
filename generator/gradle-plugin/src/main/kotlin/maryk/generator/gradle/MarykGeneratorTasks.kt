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

    @TaskAction
    fun generate() {
        SchemaBuildEngine.generate(
            schemaFiles = SchemaBuildEngine.discoverSchemas(schemas.files.map { it.toPath() }),
            packageName = packageName.get(),
            outputDirectory = outputDirectory.get().asFile.toPath(),
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

    @TaskAction
    fun update() {
        val schemaRoots = schemas.files.map { it.toPath().toAbsolutePath().normalize() }
        val schemaFiles = SchemaBuildEngine.discoverSchemas(schemaRoots)
        SchemaBuildEngine.load(schemaFiles)
        val output = baselineDirectory.get().asFile.toPath()
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
            replaceDirectory(output, staged)
        } finally {
            if (Files.exists(staged)) deleteDirectory(staged)
        }
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
}
