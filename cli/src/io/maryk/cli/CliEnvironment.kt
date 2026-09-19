package io.maryk.cli

/**
 * Provides access to environment specific helpers the CLI needs.
 */
interface CliEnvironment {
    fun resolveDirectory(path: String): DirectoryResolution
}

sealed class DirectoryResolution {
    data class Success(val normalizedPath: String) : DirectoryResolution()
    data class Failure(val message: String) : DirectoryResolution()
}

object BasicCliEnvironment : CliEnvironment {
    override fun resolveDirectory(path: String): DirectoryResolution {
        val normalized = path.trim()
        return if (normalized.isEmpty()) {
            DirectoryResolution.Failure("Directory path is required. Provide a value with `--dir`.")
        } else {
            DirectoryResolution.Success(normalized)
        }
    }
}
