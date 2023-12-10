# Enum
Contains an enumeration value. The value is limited to one of the values in an enum. 

- Kotlin Definition: `EnumDefinition`
- Kotlin Value: `IndexedEnum`
- Maryk Yaml Definition: `Enum`

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
- `enum` - defines the enum can contain

## Examples

**Example of an Enum definition**

Kotlin enum version
```kotlin
enum class Role(override val index: UInt, override val alternativeNames: Set<String>? = null): IndexedEnumComparable<Role> {
    Admin(1u),
    Moderator(2u),
    User(3u);
    
    companion object: IndexedEnumDefinition<Role>(Role::class, { entries })
}
```

Sealed class version
```kotlin
sealed class Role(index: UInt): IndexedEnumImpl<Role>(index) {
    object Admin: Role(1u)
    object Moderator: Role(2u)
    object User: Role(3u)
    
    companion object: IndexedEnumDefinition<Role>(Role::class, { arrayOf(Admin, Moderator, User) })
}
```

**Example of an Enum property definition for use within a Model**

It refers to the earlier Kotlin enum definition
```kotlin
val role by enum(
    index = 1u,
    enum = Role,
    required = true,
    final = true,
    unique = true,
    default = Role.User,
    minValue = Role.Admin,
    maxValue = Role.User
)
```

**Example of a separate Enum property definition**

It refers to the earlier Kotlin enum definition
```kotlin
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
