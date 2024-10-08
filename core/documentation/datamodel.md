# What is a DataModel?
A DataModel is a blueprint that defines the structure of the data. It contains
[property definitions](properties/properties.md), which specify the type of data, 
how it should be validated, and other relevant information. The property definitions 
are used to create data objects, which can be validated or serialized.

## Properties with Unique Identifiers

To ensure efficient data transport and storage, each property in a DataModel must have both a name and a unique integer
index. The index is used to identify the property and must remain unchanged throughout the lifetime of the application.
This index eliminates the need for reflection in code implementation, allowing everything to run quickly.

### Example of a DataModel Representing a Person

Let us consider a simple DataModel for a person, which includes their first and last names as well as their date of
birth.

To create a model for a data object within Kotlin, you start by creating a Kotlin object that extends from `RootModel`.
Within this object, you define properties by their names, indices, types, and any validations.

```kotlin
object Person : RootDataModel<Person>() {
    val firstName by string(index = 1u)
    val lastName by string(index = 2u)
    val dateOfBirth by date(index = 3u)
}
```

### Usage Example

Here's a demonstration of constructing a new Person DataObject, validating it, and creating a new key to represent it.

```kotlin
val johnSmith = Person.run {
    create(
        firstName with "John",
        lastName with "Smith",
        dateOfBirth with LocalDate(2017, 12, 5),
    )
}

// Validate the object, which will throw a PropertyValidationUmbrellaException if it's invalid
// Since there's no validation on the PropertyDefinitions, validation will succeed
Person.validate(johnSmith)

// Because no key definition was provided, this model will return a UUID-based key
val key = Person.key(johnSmith)
```

## Basic DataModels

Basic DataModels form the foundation for defining data structures. DataModels consist of properties and can be
validated. Except for RootDataModels, they can be nested within other DataModels to group data more specifically. For
example, address details can be stored within a Person DataModel.

When defining the model using Kotlin, any DataModel should extend from the Model class.

**Example**

```kotlin
object Address : DataModel<Address> {
    val streetName by string(index = 1u)
    val city by string(index = 2u)
    val zipCode by string(index = 3u)
}
```

## RootDataModel

A RootDataModel is essential for storing all DataModel structures, serving as the root element. This model has
additional methods for creating a unique key based on the data within the object. For more information about keys, refer
to the [key documentation](key.md).

RootDataModels provide ways to set a version for easy migrations and to set ordered indices for more efficient data
retrieval.

The first example above uses a RootDataModel.

Below is an example:

```kotlin
object PersonalDiaryItem : RootDataModel<Person>(
    keyDefinition = {
        Multiple(
            user.ref(),
            Reversed(dateOfPosting.ref()),
        )
    },
    indices = listOf(
        Multiple(
            user.ref(),
            tags.refToAny(),
        ),
    ),
) {
    val user by reference(index = 1u, dataModel = { User })
    val dateOfPosting by string(index = 2u)
    val message by string(index = 3u, minSize = 3, maxSize = 5)
    val tags by list(index = 4u, valueDefinition = StringDefinition())
}
```

## ValueDataModel

ValueDataModels are designed to store objects in a more compact and efficient manner compared to regular DataModels.
They are treated as values rather than objects, allowing for varied usage.

Some of the key advantages of using ValueDataModels include:

- They can be used as keys in maps, enabling fast and efficient data retrieval.
- They can be used as values in lists, making it easier to store and manage multiple values.
- They can be indexed with multiple values simultaneously, facilitating management of large amounts of data.

ValueDataModels are constructed from properties that can be represented by a fixed number of bytes. This means that
simple properties such as integers, booleans, dates, times, and floating-point numbers can be used, but more complex
properties like strings and flexible bytes cannot. Additionally, ValueDataModels cannot contain more complex properties
like sets, lists, and maps, as this would increase their size and reduce efficiency.

For example, consider a simple ValueDataModel representing a period defined by a start and end date. The properties in
this model could include both dates, all represented by fixed-size data types. This model could then be used as a key in
a map for quick information retrieval related to that period.

### Example:

**Kotlin description**

```kotlin
enum class Role(override val index: UInt, override val alternativeNames: Set<String>? = null) :
    IndexedEnumComparable<Role> {
    Admin(1u),
    Moderator(2u),
    User(3u);

    companion object : IndexedEnumDefinition<Role>(Role::class, { entries })
}

data class PersonRoleInPeriod(
    val person: Person,
    val role: Role,
    val startDate: Date,
    val endDate: Date
) : ValueDataObject(toBytes(person, role, startDate, endDate)) {
    companion object : ValueDataModel<PersonRoleInPeriod, Companion>(PersonRoleInPeriod::class) {
        val person by reference(1u, dataModel = { Person }, getter = PersonRoleInPeriod::person)
        val role by enum(2u, enum = Role, getter = PersonRoleInPeriod::role)
        val startDate by date(3u, getter = PersonRoleInPeriod::startDate)
        val endDate by date(4u, getter = PersonRoleInPeriod::endDate)

        override fun invoke(values: ObjectValues<PersonRoleInPeriod, Companion>) = PersonRoleInPeriod(
            person = values(person.index),
            role = values(role.index),
            startDate = values(startDate.index),
            endDate = values(endDate.index)
        )
    }
}
```

## Creating Derived DataModels

While it is not possible to extend DataModels in a traditional object-oriented way, it is feasible to include properties
of different types within a DataModel. This allows for the creation of a generic RootDataModel, like a Timeline, that
can contain various types of DataModels.

To achieve this, you can create a MultiTypeDefinition that maps the different types of DataModels. The property holding
the value will receive an additional type identifier. This type identifier can also be encoded into the key,
facilitating quick querying based on type.

**Example**

```kotlin
object TimelineItem : RootDataModel<TimelineItem>(
    keyDefinition = {
        Multiple(
            Reversed(dateOfPosting.ref()),
            TypeId(item)
        )
    },
    properties = Properties
) {
    val dateOfPosting by dateTime(
        index = 1u,
        final = true,
        precision = TimePrecision.SECONDS
    )

    val item by multiType(
        index = 2u,
        final = true,
        typeMap = mapOf(
            1 to EmbeddedObjectDefinition(dataModel = { Post }),
            2 to EmbeddedObjectDefinition(dataModel = { Event }),
            3 to EmbeddedObjectDefinition(dataModel = { Advertisement })
        )
    )
}
```
