# Enum
Contains an enumaration value. Is limited to one of the values in an enum

- Maryk Yaml Definition: **Enum<Name>**
- Kotlin Definition : **EnumDefinition**
- Kotlin Value: **IndexedEnum**

## Usage options
- Value
- Map Key
- Map Value
- Inside List/Set

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- index - Position in DataModel 
- indexed - Default false
- searchable - Default true

**Example of a kotlin Enum definition**
```kotlin
enum class Role(override val index: Int): IndexedEnum<Option> {
    Admin(0), Moderator(1), User(2)
}

val def = EnumDefinition<Role>(
    name = "role",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = false,
    maxValue = true
)
```

## Storage Byte representation
The enum index value as two bytes. It is encoded as unsigned short.

## Transport Byte representation
The enum index value as a VarInt.

## String representation
Name of the Enum. 