# Maryk Generator

Generate Kotlin and Proto3 source from Maryk model definitions. Choose one
generation path per schema:

- call the multiplatform generator library directly;
- use the Gradle plugin for JVM, Android, or Kotlin Multiplatform builds.

## Generator library

Read a canonical Maryk YAML definition:

Example with YAML:

```kotlin
val yaml = """
name: Person
key:
- !Ref username
? 1: username
: !String { required: true, final: true, unique: true }
""".trimIndent()

val reader = MarykYamlReader(yaml)
val context = DefinitionsConversionContext()

val model = RootDataModel.Model.Serializer.readJson(reader, context).toDataObject()
```

## Generate Kotlin

```kotlin
model.generateKotlin("package.name") { kotlinCode ->
    // write kotlinCode
}
```

## Generate Proto3 schema

Use a generation context to avoid duplicate enum/submodel output.

```kotlin
val generationContext = GenerationContext()

model.generateProto3Schema(generationContext) { protoSchema ->
    // write protoSchema
}
```

The library APIs remain useful when the caller owns file discovery or output.
The Gradle integration uses the same parser and Kotlin generator.

## Gradle plugin

Apply the plugin and configure the schema roots, generated package, output, and
compatibility baseline:

```kotlin
plugins {
    kotlin("jvm") version "2.4.0" // or Android/Kotlin Multiplatform
    id("io.maryk.generator") version "<maryk-version>"
}

dependencies {
    implementation("io.maryk:maryk-core:<maryk-version>")
}

marykGenerator {
    schemas.from("src/main/maryk")
    packageName.set("com.example.models")
    outputDirectory.set(layout.buildDirectory.dir("generated/maryk"))
    baselineDirectory.set(layout.projectDirectory.dir("schemas/baseline"))
}
```

`marykGenerateModels` recursively discovers `.yaml`, `.yml`, and `.json`
schemas, orders them by normalized path, generates stable `<Model>.kt` files,
and removes stale files only from its managed output directory. The output is
wired into JVM/Android `main`, or Kotlin Multiplatform `commonMain`.

Generation fails with the schema path and reason for missing input, malformed
definitions, or duplicate model names. Generated source is cacheable and
up-to-date when schemas, package, output, and generator version are unchanged.

## Compatibility baseline

Baselines are normal Maryk schema files. They are never changed by a check.

```bash
# Explicitly copy the current schemas into schemas/baseline.
./gradlew marykUpdateSchemaBaseline

# Compare current models against that baseline.
./gradlew marykCheckSchemaCompatibility
```

The comparison calls Maryk's existing candidate-against-stored migration rules.
Safe optional additions pass. Required additions, incompatible property changes,
and removals without the required reserved index/name declarations fail with
model/property-specific reasons. A current model with no matching baseline is a
compatible new model. A baseline model missing from current schemas fails by
default; allow intentional model removal explicitly:

```kotlin
marykGenerator {
    allowRemovedModels.set(true)
}
```

Commit baseline updates only after reviewing the compatibility result.
