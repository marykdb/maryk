package maryk.generator.gradle

import java.nio.file.Files
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
        val schemaFiles = SchemaBuildEngine.discoverSchemas(schemas.files.map { it.toPath() })
        SchemaBuildEngine.load(schemaFiles)
        val output = baselineDirectory.get().asFile.toPath()
        if (Files.exists(output)) {
            Files.walk(output).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    if (path != output) Files.deleteIfExists(path)
                }
            }
        }
        output.createDirectories()
        schemaFiles.forEach { source ->
            Files.copy(source, output.resolve(source.fileName))
        }
    }
}
