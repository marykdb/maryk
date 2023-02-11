# Maryk YAML

A streaming YAML library written in Kotlin Multiplatform.

## Writing YAML

The [`YamlWriter`](src/commonMain/kotlin/maryk/yaml/YamlWriter.kt) class provides methods for writing YAML constructions to the output.

### Creating a Writer

The [`YamlWriter`](src/commonMain/kotlin/maryk/yaml/YamlWriter.kt) constructor takes a single argument:
 
- `writer` - a function that consumes a `String` and adds it to the output.

Here is an example of how to construct a writer that writes to a string:
```kotlin
var output = buildString {
    val yamlWriter = YamlWriter {
        append(it)
    }

    // write YAML    
}
```

### Writing with the YamlWriter

The [`YamlWriter`](src/commonMain/kotlin/maryk/yaml/YamlWriter.kt) class has several methods for writing different YAML constructions:

Methods compatible with the [`JsonWriter`](../json/README.md) interface:

- `writeStartObject(isCompact: Boolean)` - Writes the start of a new object. If `isCompact` is `true` it will use the inline 
   `{fieldName: value}` syntax. If `false` (the default), it will add newlines for each field
- `writeEndObject()` - Closes an Object.
- `writeStartArray(isCompact: Boolean)` - Writes the start of a new array. If isCompact is 'true' it will use the inline `[a1, a2, a3]`
   syntax and with `false` (default) it will use `- a1` with every value on a new line and indented.
- `writeEndArray()` - Closes an array
- `writeFieldName(name: String)` - Writes the field name for an object. Adds `:` at the end.
- `writeString(value: String)` or `writeValue(value: String)` - Writes a string value. Automatically uses 'quotes' if the content could be interpreted as YAML syntax.
- `writeInt(int: Int)` - Writes an integer value.
- `writeFloat(float: Float)` - Writes a float value.
- `writeBoolean(boolean: Boolean)` - Writes a boolean value.
- `writeNull()` - Writes a null value.

Methods for writing YAML specific constructs:
- `writeTag(tag: String)` - Writes a yaml tag. Includes any preceding `!`.
- `writeStartComplexField()` - Writes the start of a complex field within a map. These fields are preceded by a `?` and 
  can contain arrays and objects.
- `writeEndComplexField()` - writes the end of a complex field name and closes it with a `: ` on a new line.



Here is an example of how to write YAML to an earlier defined yamlWriter:
```kotlin
yamlWriter.writeStartObject()
yamlWriter.writeFieldName("name")
yamlWriter.writeString("John Smith")
yamlWriter.writeFieldName("age")
yamlWriter.writeInt(32)

yamlWriter.writeFieldName("pets")
yamlWriter.writeStartArray()
yamlWriter.writeStartObject()
yamlWriter.writeFieldName("name")
yamlWriter.writeString("Muffin")
yamlWriter.writeFieldName("type")
yamlWriter.writeString("CAT")
yamlWriter.writeEndObject()
yamlWriter.writeEndArray()
yamlWriter.writeEndObject()

yamlWriter.writeEndObject()
```

An example using the apply syntax in Kotlin
```kotlin
val outputWriter = ...

YamlWriter(
    writer = outputWriter
).apply {
    writeStartObject()
    writeFieldName("name")
    writeString("John Smith")
    writeFieldName("age")
    writeInt(32)
    writeEndObject()
    writeFieldName("pets")
    writeStartArray()
    writeStartObject()
    writeFieldName("name")
    writeString("Muffin")
    writeFieldName("type")
    writeString("CAT")
    writeEndObject()
    writeEndArray()
    writeEndObject()
}
```

The result:
```yaml
name: John Smith
age: 32
pets:
- name: Muffin
  type: CAT
```

## Reading YAML

You can read YAML values by constructing a [`YamlReader`](src/commonMain/kotlin/maryk/yaml/YamlReader.kt) object. 
The YamlReader reads YAML elements represented by tokens. The `YamlReader` takes a reader function that returns one character at a time.
This way, you can use any output stream implementation or string reader from any framework.

### Constructing a YamlReader

To construct a YamlReader, you can use following parameters:

- `defaultTag` - The default application specific local tag definition used for single `!` tags. 
  Example: `tag:maryk.io,2018:`. This parameter is optional.
- `tagMap` - A map that maps tag namespaces to a map with tag names and TokenTypes to return if encountered.
- `allowUnknownTags` - A boolean value that determines if the reader should return tags that are not present in the `tagMap` as an `UnknownTag`. The default value is false.
- `reader` - a function which only takes one `Char` at a time. Implement it with fitting input stream for platform.

Here's an example of constructing a YamlReader to read YAML from a simple string:
```kotlin
val yaml = ... // Yaml String
var index = 0

val reader = YamlReader { 
  json[index++].also {
     // JS platform returns a 0 control char when nothing can be read
     // If not included the reader will never stop
     if (it == '\u0000') throw Throwable("0 char encountered")
  }
}
```

#### Read for tokens

To start reading for tokens, you call the `nextToken()` method on the `YamlReader` instance. 
Each time the method finds a token, it writes it to the public property currentToken and 
returns the value. The first `currentToken` is always `JsonToken.StartDocument`.

The following are the returnable tokens:

- `JsonToken.StartDocument` - This is the first value of `currentToken`.
- `JsonToken.EndDocument` -  If the last object or array was closed.
- `JsonToken.StartObject` - When the start of an object was read. The object can be of type [`Map`](http://yaml.org/type/map.html), 
  [`OrderedMap`](http://yaml.org/type/omap.html) or [`Pairs`](http://yaml.org/type/pairs.html)
- `JsonToken.EndObject` - When the end of an object was read.
- `JsonToken.StartArray` - When the start of a YAML sequence was read. Can be of type 
  [`Sequence`](http://yaml.org/type/seq.html) or [`Set`](http://yaml.org/type/set.html)
- `JsonToken.EndArray` - When the end of a YAML sequence was read.
- `JsonToken.FieldName` - When a field name was read inside an object. The name is in the `name` property.
- `JsonToken.Value` - When a value was read inside object or array. 'value' contains value and
  is native type defined by `type` property. This could be set with a tag in yaml or autodetected by content.
  Types can be the JSON types of [`String`](http://yaml.org/type/str.html), [`Boolean`](http://yaml.org/type/bool.html), 
  [`Int`](http://yaml.org/type/int.html), [`Float`](http://yaml.org/type/float.html) or 
  [`Null`](http://yaml.org/type/null.html) but also the [Yaml types](http://yaml.org/type/) of 
  [`Timestamp`](http://yaml.org/type/timestamp.html), [`Binary`](http://yaml.org/type/binary.html), 
  [`Merge`](http://yaml.org/type/merge.html), [`Value`](http://yaml.org/type/value.html) and 
  [`Yaml`](http://yaml.org/type/yaml.html).
  Value can also return some of the specified types from the tagMap in the constructor when encountering a tag or if set
  with `allowUnknownTags` = `true` in constructor any `UnknownTag` for local tags preceded with a single `!`. These are 
  also called application specific local tags. 

- `JsonToken.StartComplexFieldName` - When a complex field name was encountered like a sequence or a map.
- `JsonToken.EndComplexFieldName` - When the end of a complex field name was encountered.

Exception tokens:

- `JsonToken.Stopped` - When reader was actively stopped by end of document, suspended or more. All tokens
  that stop the reader extend from `JsonToken.Stopped`.
- `JsonToken.Suspended` - Extends Stopped. When reader was cut off early and has nothing more to read. 
- `JsonToken.JsonException` - Extends Stopped. When reader encountered an Exception while reading. This exception 
  was thrown earlier by `nextToken()`

Example of reading a YAML file: 
```kotlin
val input = """
name: John Smith
age: 32
"""
var index = 0

YamlReader { 
  input[index++].also {
      // JS platform returns a 0 control char when nothing can be read
      // If not included the reader will never stop
      if (it == '\u0000') throw Throwable("0 char encountered")
  }
}.apply {
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

The current line and column number of the reader can be accessed using the `lineNumber` and `columnNumber` properties. 
This allows you to track the start and end positions of the tokens.

#### Skipping fields

Within objects, it is possible to skip fields of any complexity, including multi-layered arrays and objects,
by using the `skipUntilNextField()` method. This method takes a function as an argument that consumes any skipped
JsonToken. The `pushToken()` method can be used to return any found tokens to the current `YamlReader`, allowing for 
later parsing if the YAML is dependent on other content.

#### Other YAML features

- The reader supports the `&anchor` and `*alias` tags for storing and reusing elements within a YAML document.
- Maps can be merged into other maps using the `<<` field name, making it easier to reuse map contents when combined with `*alias` tags. 
  reuse map contents.
