# Maryk Generator

Generate Kotlin and Proto3 schema from Maryk model definitions.

## Flow

1. Read a model definition (YAML/JSON/ProtoBuf).
2. Convert to a `RootDataModel`.
3. Generate Kotlin or Proto3 output.

## Read model definition

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
