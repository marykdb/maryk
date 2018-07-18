## Serializing Maryk DataObjects

Maryk DataObjects can be serialized into three different formats: [YAML](#yaml), [JSON](#json)
and [ProtoBuf](#protobuf).

ProtoBuf is the preferred small format. YAML is preferred if humans need to be able to read and edit it.
JSON is preferred if it is needed to be parsed by external libraries.

### YAML
YAML was designed as a format which is easy to be edited and read by humans. It
relies by default on indentation to make it readable. YAML provides some extra 
functionalities on top of JSON like anchors/aliases, complex field names, comment
support to make it the ideal sharable way to define model definitions, queries, and 
data objects.

Maryk includes its own streaming [YAML library](../../yaml/README.md) to ensure the same
functionality on all platforms within Maryk code.

Example DataModel definition
```yaml
name: User
key:
- !Ref username
properties:
  ? 0: username
  : !String
    required: true
    final: true
    unique: true
  ? 1: email
  : !String
    required: true
    unique: true
```

Example User object
```yaml
username: john.smith
email: john.smith@gmail.com
```

Serializing a User to YAML
```kotlin
val user: Values<User, User.Properties> // instance of a user
val yamlWriter: YamlWriter // instance of YAML writer

User.writeJson(user, yamlWriter)

```

Serializing a User from YAML
```kotlin
val yamlReader: YamlReader // instance of YAML reader

val user: Values<User, User.Properties> = User.readJson(yamlReader)
```

### JSON
JSON has the advantage of being a widely adopted human readable format which is easily
parsable by many libraries on all platforms. For this reason it was included to more 
easily debug and to easily share data with third parties not using Maryk. JSON can be
outputted in pretty mode which includes more whitespace to further enhance readability. 
The JSON is read in a streaming way for quicker results and less memory consumption.

Maryk includes its own streaming [JSON library](../../json/README.md) to ensure the same 
functionality on all platforms within Maryk code.

Example DataModel definition in JSON
```json
{
  "name": "User",
  "key": [["Ref", "username"]],
  "properties": [{
    "index": 0,
    "name": "username",
    "definition": ["String", {
      "required": true,
      "final": true,
      "unique": true
    }]
  }, {
    "index": 1,
    "name": "email",
    "definition": ["String", {
      "required": true,
      "unique": true
    }]
  }]
}
```


Example User object
```json
{
  "username": "john.smith",
  "email": "john.smith@gmail.com"
}
```

Serializing a User to JSON
```kotlin
val user: Values<User, User.Properties> // instance of a user
val jsonWriter: JsonWriter // instance of JSON writer

User.writeJson(user, jsonWriter)

```

Serializing a User from JSON
```kotlin
val jsonReader: JsonReader // instance of JSON reader

val user: Values<User, User.Properties> = User.readJson(reader)
```

### ProtoBuf
ProtoBuf was chosen because it is a widely adopted and very efficient byte
serialization format. The bytes can be read and written in a streaming way for 
faster parsing and less memory usage. [Read more here.](protobuf.md)

Serializing a User to ProtoBuf
```kotlin
val user: Values<User, User.Properties> // instance of a user
val byteWriter: (Byte) -> Unit // instance of byte writer writer

val cache = WriteCache()

// Calculate first total byte length to write
val byteLength = User.calculateProtoBufLength(user, cache)

// Reserve space on your byte writer.

User.writeProtoBuf(user, cache, byteWriter)
```

Serializing a User from ProtoBuf
```kotlin
val jsonReader: JsonReader // instance of JSON reader

val byteReader: () -> Byte 
val byteLength: Int // Amount of bytes to read, was probably in request

val user = User.readProtoBuf(byteLength, byteReader)

// It is also possible to get only a map of index:value pairs.
// This is useful if there is not a generated model.
val user: Values<User, User.Properties> = User.readProtoBuf(byteLength, byteReader)
```
