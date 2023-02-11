# Maryk Generator


The Maryk Generator is a tool that generates code and schemas from Maryk models. It currently supports generating Kotlin 
code and ProtoBuf schema representations of data models.

# Generating Kotlin Code

To generate Kotlin code, you first need to read the serialized representation of the model. This model representation
can be defined in YAML, ProtoBuf, or JSON format.


## Reading YAML and generate Data model
```kotlin
    val reader = MarykYamlReader {
        // read source. Iterate over char
    }

    val newContext = DefinitionsConversionContext()

    val model = RootDataModel.Model.readJson(reader, newContext).toDataObject()
```

## Generating Kotlin code from Data model

```kotlin
    model.generateKotlin("package.name") { kotlinCode: String ->
        // Do something with Kotlin code
    }
```
## Generating ProtoBuf Schema

To generate the ProtoBuf schema, you first need to create a `GenerationContext` instance so
it can store any encountered submodels and enums which are found in the models. This way
code is only generated once for them.

```kotlin
    // The Context stores values which should be known at time of processing of model like enums
    val generationContext = GenerationContext()
```

Then, you can generate the ProtoBuf schema like this:

```kotlin
    model.generateProto3Schema(generationContext) { protoBufSchema: String ->
        // Do something with protobuf schema
    }
```
