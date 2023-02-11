## Serializing Maryk DataObjects

Maryk DataObjects can be serialized into three formats: [YAML](#yaml), [JSON](#json)
and [ProtoBuf](#protobuf).

ProtoBuf is the preferred format for its small size, while YAML is preferred when human-readability is required. 
JSON is preferred when it needs to be parsed by external libraries.

### YAML
YAML is a human-readable format that relies on indentation to make it easy to read and edit. Maryk includes its own
[YAML library](../../yaml/README.md), which provides functionality such as anchors/aliases, complex field names, and comment support. This makes 
YAML an ideal format for defining model definitions, queries, and data objects.

Here is an example of a DataModel definition in YAML:
```yaml
name: User
key:
- !Ref username
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

And an example User object in YAML:
```yaml
username: john.smith
email: john.smith@gmail.com
```

Serializing a User to YAML in Kotlin:
```kotlin
val user: Values<User, User.Properties> // instance of a user
val yamlWriter: YamlWriter // instance of YAML writer

User.writeJson(user, yamlWriter)

```

Deserializing a User from YAML in Kotlin:
```kotlin
val yamlReader: YamlReader // instance of YAML reader

val user: Values<User, User.Properties> = User.readJson(yamlReader)
```

### JSON
JSON is a widely adopted and human-readable format that is easily parsable by many libraries across all platforms.
This makes it easy to debug and share data with third-party systems not using Maryk. Additionally, JSON can be outputted 
in "pretty mode," which includes extra whitespace to further enhance readability. 
Maryk includes its own streaming [JSON library](../../json/README.md) to ensure consistent functionality across all platforms.

Here is an example of a DataModel definition in JSON:
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


And here is an example of a User object in JSON:
```json
{
  "username": "john.smith",
  "email": "john.smith@gmail.com"
}
```

To serialize a User object to JSON, use the following code:
```kotlin
val user: Values<User, User.Properties> // instance of a user
val jsonWriter: JsonWriter // instance of JSON writer

User.writeJson(user, jsonWriter)

```

To deserialize a User object from JSON, use the following code:
```kotlin
val jsonReader: JsonReader // instance of JSON reader

val user: Values<User, User.Properties> = User.readJson(reader)
```

### ProtoBuf
ProtoBuf is a widely adopted and highly efficient byte serialization format. Its ability to read and write bytes in a 
streaming manner allows for faster parsing and reduces memory usage. 
[Read more here.](protobuf.md)

Here is an example of serializing a User object to ProtoBuf:
```kotlin
val user: Values<User, User.Properties> // instance of a user
val byteWriter: (Byte) -> Unit // instance of byte writer

val cache = WriteCache()

// Calculate first total byte length to write
val byteLength = User.calculateProtoBufLength(user, cache)

// Reserve space on your byte writer.

User.writeProtoBuf(user, cache, byteWriter)
```

And here is an example of deserializing a User object from ProtoBuf:
```kotlin
val jsonReader: JsonReader // instance of JSON reader

val byteReader: () -> Byte 
val byteLength: Int // Amount of bytes to read, probably defined in the request

val user = User.readProtoBuf(byteLength, byteReader)
```

It is also possible to get only a map of index-value pairs when deserializing from ProtoBuf. This is useful when there is not a generated model available.
```kotlin
val user: Values<User, User.Properties> = User.readProtoBuf(byteLength, byteReader)
```
