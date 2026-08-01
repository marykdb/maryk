package maryk.generator.gradle

import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class MarykGeneratorExtension @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout,
) {
    val schemas: ConfigurableFileCollection = objects.fileCollection()
    val packageName: Property<String> = objects.property(String::class.java)
    val outputDirectory: DirectoryProperty = objects.directoryProperty()
    val baselineDirectory: DirectoryProperty = objects.directoryProperty()
    val allowRemovedModels: Property<Boolean> = objects.property(Boolean::class.java)

    init {
        schemas.from(layout.projectDirectory.dir("src/main/maryk"))
        outputDirectory.convention(layout.buildDirectory.dir("generated/maryk"))
        baselineDirectory.convention(layout.projectDirectory.dir("schemas/baseline"))
        allowRemovedModels.convention(false)
    }
}
