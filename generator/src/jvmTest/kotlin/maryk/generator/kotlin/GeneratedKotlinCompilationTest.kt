package maryk.generator.kotlin

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.reference
import maryk.core.properties.definitions.string
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

private object `nested-model` : DataModel<`nested-model`>() {
    val `finally` by string(index = 1u)
}

private object `typeof` : RootDataModel<`typeof`>(
    indexes = {
        `typeof`.run {
            listOf(`by`.ref(), `nested-model`.`finally`.ref(nested.ref()))
        }
    }
) {
    val `by` by string(index = 1u)
    val `finally` by enum(index = 2u, enum = `when`, default = `when`.`finally`)
    val nested by embed(index = 3u, dataModel = { `nested-model` })
    val `import` by reference(index = 4u, dataModel = { `catch` })
}

class GeneratedKotlinCompilationTest {
    @Test
    fun compilesGeneratedKeywordModelsWithNestedDefaultAndIndexReferences() {
        val packageName = "example.generated"
        val sources = listOf(
            buildString { `catch`.generateKotlin(packageName) { append(it) } },
            buildString { `when`.generateKotlin(packageName) { append(it) } },
            buildString { `nested-model`.generateKotlin(packageName) { append(it) } },
            buildString {
                `typeof`.generateKotlin(
                    packageName,
                    GenerationContext(enums = mutableListOf(`when`)),
                ) { append(it) }
            },
        )
        val sourceDirectory = createTempDirectory()
        val outputDirectory = createTempDirectory()
        val sourceFiles = sources.mapIndexed { index, source ->
            sourceDirectory.resolve("Generated$index.kt").also { it.writeText(source) }
        }
        val compilerOutput = ByteArrayOutputStream()

        val result = K2JVMCompiler().exec(
            PrintStream(compilerOutput),
            "-classpath", System.getProperty("java.class.path"),
            "-no-stdlib", "-no-reflect",
            "-d", outputDirectory.toString(),
            "-jvm-target", "17",
            *sourceFiles.map { it.toString() }.toTypedArray(),
        )

        assertEquals(ExitCode.OK, result, compilerOutput.toString())
    }
}
