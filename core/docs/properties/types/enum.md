# Enum
Represents a value chosen from a predefined enum.

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
- `minValue` - default unset. Minimum value
- `maxValue` - default unset. Maximum value

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
    
    companion object: IndexedEnumDefinition<Role>(Role::class, { listOf(Admin, Moderator, User) })
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

A written value's index must be resolvable by the configured definition.
Unknown indexes require an `unknownCreator`; otherwise validation rejects them
before storage. Memory resolves ordinary enum cells in the same way as
persistent readers.

Reserving a removed case's index and name prevents reuse, but stored values
still need a reader. Removing that reader requires a model migration. Multi
type cases must retain their declared payload definition until their stored
values have been migrated.

Indexes above 65,535 are rejected in declarations, value validation, resolution
and binary encoding, including unknown enum cases. Existing supported indexes
keep the same bytes; no data migration is needed. Previously truncated indexes
cannot be recovered from their stored bytes alone.

## Transport Byte representation
The enum index value as a VarInt.

## String representation
Name of the Enum. 
