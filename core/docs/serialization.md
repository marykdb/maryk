# Serializing

Maryk DataObjects can be serialised to [YAML](#yaml), [JSON](#json) or [ProtoBuf](#protobuf). Each format has its own strengths:

- **ProtoBuf** – compact and efficient.
- **YAML** – human‑readable.
- **JSON** – interoperable with external systems.

### YAML

YAML is a human-readable data serialization format that uses indentation to structure data. It is particularly useful 
for configuration files and data exchange between languages with different data structures. Maryk provides its own 
[YAML library](../../yaml/README.md), which supports features such as anchors, aliases, complex field names, and 
comments. This makes YAML an excellent choice for defining model definitions, queries, and data objects.

#### Example: DataModel Definition in YAML

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

#### Example: User Object in YAML

```yaml
username: john.smith
email: john.smith@gmail.com
```

#### Serializing a User to YAML in Kotlin

```kotlin
val user: Values<User, User.Properties> // instance of a user
val yamlWriter: YamlWriter // instance of YAML writer

User.writeYaml(user, yamlWriter)
```

#### Deserializing a User from YAML in Kotlin

```kotlin
val yamlReader: YamlReader // instance of YAML reader

val user: Values<User, User.Properties> = User.readYaml(yamlReader)
```

### JSON

JSON (JavaScript Object Notation) is a lightweight data interchange format that is easy for humans to read and write, 
and easy for machines to parse and generate. It is widely used across various platforms and programming languages. 
Maryk includes its own streaming [JSON library](../../json/README.md) to ensure consistent functionality across all 
supported platforms. JSON can also be formatted in "pretty mode" for enhanced readability.

#### Example: DataModel Definition in JSON

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

#### Example: User Object in JSON

```json
{
  "username": "john.smith",
  "email": "john.smith@gmail.com"
}
```

#### Serializing a User to JSON in Kotlin

```kotlin
val user: Values<User, User.Properties> // instance of a user
val jsonWriter: JsonWriter // instance of JSON writer

User.writeJson(user, jsonWriter)
```

#### Deserializing a User from JSON in Kotlin

```kotlin
val jsonReader: JsonReader // instance of JSON reader

val user: Values<User, User.Properties> = User.readJson(jsonReader)
```

### ProtoBuf

ProtoBuf (Protocol Buffers) is a language-neutral, platform-neutral extensible mechanism for serializing structured data. 
It is highly efficient in terms of both speed and space, making it suitable for high-performance applications. For more 
details, [read here](protobuf.md).

#### Example: Serializing a User Object to ProtoBuf

```kotlin
val user: Values<User, User.Properties> // instance of a user
val byteWriter: (Byte) -> Unit // instance of byte writer

val cache = WriteCache()

// Calculate the total byte length to write
val byteLength = User.calculateProtoBufLength(user, cache)

// Reserve space on your byte writer
User.writeProtoBuf(user, cache, byteWriter)
```

#### Example: Deserializing a User Object from ProtoBuf

```kotlin
val byteReader: () -> Byte // function to read bytes
val byteLength: Int // Amount of bytes to read, typically defined in the request

val user = User.readProtoBuf(byteLength, byteReader)
```

#### Getting Index-Value Pairs from ProtoBuf

It is also possible to obtain a map of index-value pairs when deserializing from ProtoBuf. This is useful when a 
generated model is not available.

```kotlin
val user: Values<User, User.Properties> = User.readProtoBuf(byteLength, byteReader)
```
