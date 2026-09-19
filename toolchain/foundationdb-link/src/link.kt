package maryk.build

import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun prepareFoundationDbLink(
    @Input(inferTaskDependency = false) projectRoot: Path,
    @Output defFile: Path,
) {
    // Provisioning belongs to the managed test command, under its server lease.
    // Generating link settings must never replace a running server's library.
    val library = projectRoot.resolve("store/foundationdb/bin/lib")
    defFile.parent.createDirectories()
    val definition = "linkerOpts = -L\"$library\" -lfdb_c -rpath \"$library\"\n"
    if (!defFile.exists() || defFile.readText() != definition) defFile.writeText(definition)
}
