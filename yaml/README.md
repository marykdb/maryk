# Maryk YAML

A streaming YAML library written in Kotlin for JS and JVM platforms

## Writing YAML

### Constructing a writer

The [`YamlWriter`](common/src/main/kotlin/maryk/yaml/YamlWriter.kt) constructor takes 
1 property:
 
- `writer` - A function which consumes a `String` to be added to output.

Constructing a writer which writes to a String
```kotlin
var output = ""

val yamlWriter = YamlWriter {
    output += it
}
```

### Writing with the YamlWriter

The [`YamlWriter`](common/src/main/kotlin/maryk/yaml/YamlWriter.kt) has a few 
methods to write YAML constructions to the output

Compatible with [`JsonWriter`](../json/README.md):

- `writeStartObject(isCompact: Boolean)` - writes a new object. If isCompact is `true` it will use the inline 
   {fieldName: value} syntax and with `false` (default) it will newline any new field
- `writeEndObject()` - closes an Object
- `writeStartArray(isCompact: Boolean)` - writes a new array. If isCompact is 'true' it will use the inline [a1, a2, a3]
   syntax and with false (default) it will use `- a1` with every value on a new line and indented.
- `writeEndArray()` - closes an array
- `writeFieldName(name: String)` - Field for an object. Adds `:` at the end.
- `writeString(value: String)` / `writeValue(value: String)` - Writes a string. Will automatically use 'quotes' if content 
  can be otherwise interpreted as YAML syntax.
- `writeInt(int: Int)` - writes an integer
- `writeFloat(float: Float)` - writes a float
- `writeBoolean(boolean: Boolean)` - writes a boolean
- `writeNull()` - writes a null value

Special for YAML
- `writeTag(tag: String)` - writes a yaml tag. Include any `!` preceding the tag.
- `writeStartComplexField()` - writes the start of a complex field within a map. These fields are preceded by a `?` and 
  can contain arrays and objects.
- `writeEndComplexField()` - writes the end of a complex field name and closes it with a `: ` on a new line.
 


Example of writing to above defined `yamlWriter`
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

Using apply syntax in Kotlin
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

Result:
```yaml
name: John Smith
age: 32
pets:
- name: Muffin
  type: CAT
```

## Reading YAML

To read YAML values you need to construct a [`YamlReader`](common/src/main/kotlin/maryk/yaml/YamlReader.kt)
which can then read the YAML for found tokens which represents YAML elements. `YamlReader`
takes a `reader` which is a function to return 1 char at a time. This way any 
outputStream implementation or String reader from any framework can be used.

Constructor parameters for `YamlReader`:

- `defaultTag` - The default application specific local tag definition used for single `!` tags. 
  Example: `tag:maryk.io,2018:`. Can be null
- `tagMap` - maps tag namespaces to a map with tag names and TokenTypes to return if encountered.
- `allowUnknownTags` - set to true if reader should return tags which are not preset in the tagMap as an `UnknownTag`. 
  Default is `false`
- `reader` - a function which only takes one `Char` at a time. Implement it with fitting input stream for platform.

Constructing a reader to read Yaml from a simple String. We recommend to use proper output streams fitting the platform.
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

To begin reading for tokens you start to call `nextToken()` on the `YamlReader` instance.
Each time it finds a token it writes it to the public property `currentToken` and returns
the value. The first `currentToken` is always `JsonToken.StartDocument`

Returnable tokens:

- `JsonToken.StartDocument` - `currentToken` starts with this value
- `JsonToken.EndDocument` - if last object or array was closed
- `JsonToken.StartObject` - when a start of object was read. Can be of type [`Map`](http://yaml.org/type/map.html), 
  [`OrderedMap`](http://yaml.org/type/omap.html) or [`Pairs`](http://yaml.org/type/pairs.html)
- `JsonToken.EndObject` - when a end of object was read
- `JsonToken.StartArray` - when start of a Yaml sequence was read. Can be of type 
  [`Sequence`](http://yaml.org/type/seq.html) or [`Set`](http://yaml.org/type/set.html)
- `JsonToken.EndArray` - when end of a Yaml sequence was read
- `JsonToken.FieldName` - when a field name was read inside an object. Name is in `name` property
- `JsonToken.Value` - when a value was read inside object or array. 'value' contains value and
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

- `JsonToken.StartComplexFieldName` - when a complex field name was encountered like a sequence or a map.
- `JsonToken.EndComplexFieldName` - when the end of a complex field name was encountered.

Exception tokens:

- `JsonToken.Stopped` - When reader was actively stopped by end of document, suspended or more. All tokens
  that stop the reader extend from `JsonToken.Stopped`.
- `JsonToken.Suspended` - Extends Stopped. When reader was cut off early and has nothing more to read. 
- `JsonToken.JsonException` - Extends Stopped. When reader encountered an Exception while reading. This exception 
  was thrown earlier by `nextToken()`

Example
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

It is possible to access the current line and column number of the reader by accessing 
`lineNumber` and `columnNumber`. This way it is possible to see where the tokens started 
and ended.

#### Skipping fields

Within objects it is possible to skip fields despite how complex the value is. Even if
they are multi layered arrays and objects `skipUntilNextField()` will skip until the next
field name. `skipUntilNextField()` takes one argument in the form of a function which
consumes any skipped JsonToken. Found tokens can be pushed to the current `YamlReader` with the
`pushToken()` method so they are returned first. In this way tokens can be parsed later if yaml is 
dependent on other content.

#### Other YAML features

- The reader can handle `&anchor` and `*alias` tags to store and reuse elements within a YAML document.
- The reader can merge maps into other maps with the `<<` field name. This is useful together with `*alias` tags to 
  reuse map contents.
