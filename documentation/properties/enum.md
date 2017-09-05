# Enum
Contains an enumaration value. Is limited to one of the values in an enum

- Kotlin Definition : **EnumDefinition**
- Maryk Yaml Definition: **Enum<Name>**

## Usage options
- Value
- Map Key
- Map Value
- List

## Validation Options
- Required
- Final
- Unique
- Minimum value
- Maximum value

## Data options
- Index - Position in DataModel 
- Indexed - Default false
- Searchable - Default true

**Example of a kotlin String definition**
```kotlin
enum class Role(override val index: Int): IndexedEnum<Option> {
    Admin(0), Moderator(1), User(2)
}

EnumDefinition<Role>(
    name = "role",
    index = 0,
    required = true,
    final = true,
    unique = true,
    minValue = false,
    maxValue = true,
)
```

## Byte representation
The enum short value as two bytes. They are encoded in an unsigned way.

## String representation
In unoptimized mode it will use the name of the Enum. 
In optimized mode the short value will be used.