# Maryk Generator

The Maryk generator generates code and schemas from Maryk models. It currently can generate Kotlin code and protobuf 
schema representation of data models. 

# Generate Kotlin model

Probably the model is represented by YAML, ProtoBuf or JSON. So it is first needed to read the serialized format.

YAML example:
```kotlin
    val reader = MarykYamlReader {
        // read source. Iterate over char
    }

    val newContext = DefinitionsConversionContext()

    val model = RootDataModel.Model.readJson(reader, newContext).toDataObject()
```

Generate Kotlin code
```kotlin
    model.generateKotlin("package.name") { kotlinCode: String ->
        // Do something with Kotlin code
    }
```

Generate ProtoBuf schema
```kotlin
    // Store values which should be known at time of processing of model like enums
    // in the context
    val generationContext = GenerationContext()

    model.generateProto3Schema(generationContext) { protoBufSchema: String ->
        // Do something with protobuf schema
    }
```
