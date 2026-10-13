package maryk.conventions

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class MavenUploadService : BuildService<BuildServiceParameters.None>
