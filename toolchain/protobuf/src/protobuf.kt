package maryk.build

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

@TaskAction
fun provisionProtoc(@Input catalog: Path, @Input verificationMetadata: Path, @Output bin: Path) {
    val version = Regex("""(?m)^protoc = "([^"]+)"$""").find(catalog.readText())?.groupValues?.get(1)
        ?: error("No protoc version in $catalog")
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val classifier = when {
        os.contains("mac") && arch in setOf("aarch64", "arm64") -> "osx-aarch_64"
        os.contains("mac") && arch in setOf("x86_64", "amd64") -> "osx-x86_64"
        os.contains("linux") && arch in setOf("x86_64", "amd64") -> "linux-x86_64"
        os.contains("windows") && arch in setOf("x86_64", "amd64") -> "windows-x86_64"
        else -> error("No verified protoc executable for $os/$arch")
    }
    val artifact = "protoc-$version-$classifier.exe"
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val document = factory.newDocumentBuilder().parse(verificationMetadata.toFile())
    val artifacts = document.getElementsByTagName("artifact")
    val checksums = buildSet {
        for (i in 0 until artifacts.length) {
            val node = artifacts.item(i)
            if (node.attributes.getNamedItem("name")?.nodeValue != artifact) continue
            val children = node.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeName == "sha256") add(child.attributes.getNamedItem("value").nodeValue)
            }
        }
    }
    check(checksums.isNotEmpty()) { "No SHA-256 pinned for $artifact in $verificationMetadata" }
    bin.createDirectories()
    val executable = bin.resolve("protoc.exe")
    val downloaded = Files.createTempFile(bin, "protoc-", ".download")
    try {
        val connection = URI("https://repo.maven.apache.org/maven2/com/google/protobuf/protoc/$version/$artifact")
            .toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 60_000 }
        connection.getInputStream().use { input -> Files.copy(input, downloaded, REPLACE_EXISTING) }
        val actual = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(downloaded))
            .joinToString("") { "%02x".format(it) }
        check(actual in checksums) { "SHA-256 mismatch for $artifact: $actual" }
        Files.move(downloaded, executable, REPLACE_EXISTING)
        check(executable.toFile().setExecutable(true)) { "Cannot make $executable executable" }
    } finally {
        Files.deleteIfExists(downloaded)
    }
}

@TaskAction
fun generateProto(@Input bin: Path, @Input sourceDir: Path, @Output javaOutputDir: Path, @Output resourceOutputDir: Path) {
    check(javaOutputDir.toFile().deleteRecursively()) { "Cannot clear $javaOutputDir" }
    javaOutputDir.createDirectories()
    check(resourceOutputDir.toFile().deleteRecursively()) { "Cannot clear $resourceOutputDir" }
    resourceOutputDir.createDirectories()
    Files.walk(sourceDir).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { source ->
            val target = resourceOutputDir.resolve(sourceDir.relativize(source))
            target.parent.createDirectories()
            Files.copy(source, target, REPLACE_EXISTING)
        }
    }
    val sources = Files.walk(sourceDir).use { paths ->
        paths.filter { it.toString().endsWith(".proto") }.sorted().map { it.toString() }.toList()
    }
    check(sources.isNotEmpty()) { "No protobuf fixtures in $sourceDir" }
    val command = listOf(bin.resolve("protoc.exe").toString(), "--java_out=$javaOutputDir", "-I$sourceDir") + sources
    check(ProcessBuilder(command).inheritIO().start().waitFor() == 0) { "protoc failed" }
}
