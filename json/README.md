# Maryk JSON

A streaming JSON library written in Kotlin for JS and JVM platforms

## Writing JSON

### Constructing a writer

The [`JsonWriter`](src/commonMain/kotlin/maryk/json/JsonWriter.kt) constructor takes 
2 properties:
 
- `pretty` - Default is `false`. If set to `true` the writer will add whitespace
  for easier readability.
- `writer` - A function which consumes a `String` to be added to output.

Constructing a writer which writes to a String
```kotlin
var output = ""

val jsonWriter = JsonWriter(pretty = true) {
    output += it
}
```

### Writing with the JsonWriter

The [`JsonWriter`](src/commonMain/kotlin/maryk/json/JsonWriter.kt) has a few 
methods to write JSON constructions to the output

- `writeStartObject()` - writes a `{`
- `writeEndObject()` - writes a `}`
- `writeStartArray()` - writes a `[`
- `writeEndArray()` - writes a `]`
- `writeFieldName(name: String)` - Field for an object. Adds `:` at the end.
- `writeString(value: String)` - Adds `"` around a String value for arrays or object
- `writeInt(int: Int)` - writes an integer
- `writeFloat(float: Float)` - writes a float
- `writeBoolean(boolean: Boolean)` - writes a boolean
- `writeNull()` - writes a null value
- `writeValue(value: String)` - Value in object or array. The Json writer writes it
  as a string without quotes.

Example of writing to above defined `jsonWriter`
```kotlin
jsonWriter.writeStartObject()
jsonWriter.writeFieldName("name")
jsonWriter.writeString("John Smith")
jsonWriter.writeFieldName("age")
jsonWriter.writeInt(32)
jsonWriter.writeEndObject()
```

Using apply syntax in Kotlin
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

Result:
```json
{
    "name": "John Smith",
    "age": 32
}
```

## Reading JSON

To read JSON values you need to construct a [`JsonReader`](src/commonMain/kotlin/maryk/json/JsonReader.kt)
which can then read the JSON for found tokens which represents JSON elements. `JsonReader`
only takes a `reader` which is a function to return 1 char at a time. This way any 
outputStream implementation or String reader from any framework can be used.

Constructing a reader to read JSON from a simple String:
```kotlin
val json = ... // Json String
var index = 0

val reader = JsonReader { 
    json[index++] 
}
```

#### Read for tokens

To begin reading for tokens you start to call `nextToken()` on the `JsonReader` instance.
Each time it finds a token it writes it to the public property `currentToken` and returns
the value. The first `currentToken` is always `JsonToken.StartDocument`

Returnable tokens:

- `JsonToken.StartDocument` - `currentToken` starts with this value
- `JsonToken.EndDocument` - if last object or array was closed
- `JsonToken.StartObject` - when a start of object was read: '{'
- `JsonToken.EndObject` - when a end of object was read: '}'
- `JsonToken.StartArray` - when start of a JSON array was read: '['
- `JsonToken.EndArray` - when end of a JSON array was read: '['
- `JsonToken.FieldName` - when a field name was read inside an object. Name is in `name` property
- `JsonToken.Value` - when a value was read inside object or array. 'value' contains value and
  is native type defined by `type` property. Types can be `String`, `Boolean`, `Int`, `Float` or `Null`


Exception tokens:

- `JsonToken.Stopped` - When reader was actively stopped by end of document, suspended or more. All tokens
  that stop the reader extend from `JsonToken.Stopped`.
- `JsonToken.Suspended` - Extends Stopped. When reader was cut off early and has nothing more to read. 
- `JsonToken.JsonException` - Extends Stopped. When reader encountered an Exception while reading. This exception 
  was thrown earlier by `nextToken()`

Example
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

It is possible to access the current line and column number of the reader by accessing 
`lineNumber` and `columnNumber`. This way it is possible to see where the tokens started 
and ended.

#### Skipping fields

Within objects it is possible to skip fields despite how complex the value is. Even if
they are multi layered arrays and objects `skipUntilNextField()` will skip until the next
field name. `skipUntilNextField()` takes one argument in the form of a function which
consumes any skipped JsonToken. It is possible to collect those tokens in a 
[`PresetJsonTokenReader`](src/commonMain/kotlin/maryk/json/PresetJsonTokenReader.kt) to 
later parse them.
