# Maryk JSON

A streaming JSON library written in Kotlin for Multiplatform usage

## Writing JSON

The [`JsonWriter`](src/commonMain/kotlin/maryk/json/JsonWriter.kt) class provides an interface for constructing JSON data. It takes two properties:

- `pretty` - Default is `false`. If set to `true` the writer will add whitespace
  for easier readability.
- `writer` - A function which consumes a `String` to be added to output.

Here's an example of how to construct a writer that writes to a string:
```kotlin
var output = buildString { 
    JsonWriter(pretty = true) {
        append(it)
    }
}
```

### Writing with the JsonWriter

The [`JsonWriter`](src/commonMain/kotlin/maryk/json/JsonWriter.kt) class has several 
methods for writing different elements of a JSON document:

- `writeStartObject()` - Writes a `{` character
- `writeEndObject()` - Writes a `}` character
- `writeStartArray()` - Writes a `[` character
- `writeEndArray()` - Writes a `]` character
- `writeFieldName(name: String)` - Writes a field name for an object, followed by a colon.
- `writeString(value: String)` - Writes a string value, surrounded by quotes.
- `writeInt(int: Int)` - Writes an integer value.
- `writeFloat(float: Float)` - Writes a floating-point value.
- `writeBoolean(boolean: Boolean)` - Writes a boolean value.
- `writeNull()` - Writes a `null` value.
- `writeValue(value: String)` - Writes a value without quotes.

Here's an example of how to write a simple JSON object using the `JsonWriter` class:
```kotlin
jsonWriter.writeStartObject()
jsonWriter.writeFieldName("name")
jsonWriter.writeString("John Smith")
jsonWriter.writeFieldName("age")
jsonWriter.writeInt(32)
jsonWriter.writeEndObject()
```

Using the apply syntax in Kotlin, the same example can be written like this:
```kotlin
val outputWriter = ...

JsonWriter(
    pretty = true,
    writer = outputWriter
).apply {
    writeStartObject()
    writeFieldName("name")
    writeString("John Smith")
    writeFieldName("age")
    writeInt(32)
    writeEndObject()
}
```

The resulting JSON string will look like this:
```json
{
    "name": "John Smith",
    "age": 32
}
```

## Reading JSON

The [`JsonReader`](src/commonMain/kotlin/maryk/json/JsonReader.kt) class provides an interface for reading JSON data. 
It takes a single property, `reader`, which is a function that returns one character at a time. This allows the reader 
to be used with any output stream implementation or string reader from any framework.

Here's an example of how to construct a reader to read JSON data from a string:
```kotlin
val json = ... // Json String
var index = 0

val reader = JsonReader { 
    json[index++] 
}
```

#### Read for tokens

To start reading for tokens, you need to call the `nextToken()` function on an instance of the `JsonReader`. 
Each time a token is found, it is written to the public property `currentToken` and returned. 
The initial value of `currentToken` is always `JsonToken.StartDocument`.

The following tokens can be returned:

- `JsonToken.StartDocument` - This is the starting value for `currentToken`.
- `JsonToken.EndDocument` - This is returned when the last object or array has been closed.
- `JsonToken.StartObject` - This is returned when the start of an object is read: '{'.
- `JsonToken.EndObject` - This is returned when the end of an object is read: '}'.
- `JsonToken.StartArray` - This is returned when the start of a JSON array is read: '['.
- `JsonToken.EndArray` - This is returned when the end of a JSON array is read: ']'.
- `JsonToken.FieldName` - This is returned when a field name is read inside an object. The field name can be accessed through the `name` property.
- `JsonToken.Value` - This is returned when a value is read inside an object or array. The value can be accessed through the `value` property and is a native type, defined by the `type` property. The available types are `String`, `Boolean`, `Int`, `Float`, or `Null`.

The following exception tokens can be returned:

- `JsonToken.Stopped` - This is returned when the reader is actively stopped due to the end of the document, suspension, or other reasons. All tokens that stop the reader extend from `JsonToken.Stopped`.
- `JsonToken.Suspended` - This extends from `JsonToken.Stopped`. This is returned when the reader is cut off early and has no more data to read. 
- `JsonToken.JsonException` - This extends from `JsonToken.Stopped`. This is returned when the reader encounters an exception while reading. The exception was thrown earlier by the `nextToken()` function.

Example:
```kotlin
val input = """{"name": "John Smith", "age": 32}"""
var index = 0

JsonReader { input[index++] }.apply {
    println(currentToken)
    
    while (currentToken !is JsonToken.Stopped) {
        println(nextToken())
    }
}
```

Output:
```text
StartDocument
StartObject
FieldName(name)
Value(John Smith)
FieldName(age)
Value(32)
EndObject
EndDocument
```

##### Line and Column numbers

You can access the current line and column numbers of the reader by accessing the `lineNumber` and `columnNumber` properties, 
respectively. This allows you to see the start and end positions of the tokens.

#### Skipping fields
When reading JSON objects, it is sometimes necessary to skip certain fields and continue reading 
the rest of the data. The JsonReader class provides a `skipUntilNextField()` method to accomplish 
this, even if the skipped field contains complex nested arrays and objects.

The `skipUntilNextField()` method takes a single argument, a function that will consume any skipped JSON tokens. 
This function allows you to collect the skipped tokens and parse them later if needed. To do this, you can use a
[`PresetJsonTokenReader`](src/commonMain/kotlin/maryk/json/PresetJsonTokenReader.kt).
