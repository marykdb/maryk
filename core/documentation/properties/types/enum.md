# Enum
Contains an enumaration value. Is limited to one of the values in an enum

- Maryk Yaml Definition: `Enum`
- Kotlin Definition: `EnumDefinition`
- Kotlin Value: `IndexedEnum`

## Usage options
- Value
- Map key or value
- Inside List/Set

## Validation Options
- `required` - default true
- `final` - default false
- `unique` - default false
- `minValue` - default false. Minimum value
- `maxValue` - default false. Maximum value

## Other options
- `default` - the default value to be used if value was not set.
- `indexed` - default false
- `searchable` - default true
- `enum` - defines the enum can contain

## Examples

**Example of a YAML Enum property definition with previously defined Enum named Role**
```yaml
!Enum
  enum:
    name: Role
  required: false
  unique: false
  final: true
  default: User
  minValue: Admin
  maxValue: User
```

**Example of a YAML Enum property definition**
```yaml
!Enum
  enum:
    name: Role
    values:
      0: Admin
      1: Moderator
      2: User
  required: false
  unique: false
  final: true
  default: User
  minValue: Admin
  maxValue: User
```

**Example of a Kotlin Enum property definition**
```kotlin
enum class Role(override val index: Int): IndexedEnum<Option> {
    Admin(0), Moderator(1), User(2);
    
    companion object: IndexedEnumDefinition<Role>("Role", Role.values())
}

val def = EnumDefinition(
    enum = Role,
    required = true,
    final = true,
    unique = true,
    default = Role.User,
    minValue = Role.Admin,
    maxValue = Role.User
)
```

## Storage Byte representation
The enum index value as two bytes. It is encoded as unsigned short.

## Transport Byte representation
The enum index value as a VarInt.

## String representation
Name of the Enum. 
