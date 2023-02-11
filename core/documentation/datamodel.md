# What is a DataModel?
A DataModel is a blueprint that defines the structure of the data. It contains
[property definitions](properties/properties.md), which specify the type of data, 
how it should be validated, and other relevant information. The property definitions 
are used to create data objects, which can be validated or serialized.

## Properties with Unique Identifiers
To ensure efficient data transport and storage, each property in a DataModel must
have both a name and a unique integer index. The index is used to identify the property
and must remain unchanged throughout the lifetime of the application. This index eliminates
the need for reflection in code implementation.

### Example of a DataModel representing a Person in name and date of birth

Let us consider a simple DataModel for a person, which includes their first + last name and date of birth.

To define the model within YAML, you create a basic object with a name and you add the names, indices, types and any
validations

**Maryk Model YAML:**
```yaml
name: Person
? 1: firstName
: !String
? 2: lastName
: !String
? 3: dateOfBirth
: !Date
```

To create a model for a storable data object within Kotlin, you start with creating a kotlin object which extends from
`RootDataModel`. Within you include a Properties object which defines the names, indices, types and any validations of
any of the properties. Furthermore, you add an invoke object to easily instantiate a data object with its properties.

**Kotlin implementation.** Can be generated from Maryk Model YAML
```kotlin
object Person : RootDataModel<Person, Person.Properties>(
    properties = Properties
){ 
    object Properties: PropertyDefinitions() {
        val firstName by string(1u)
        val lastName by string(2u)
        val dateOfBirth by date(3u)
    }

    operator fun invoke(
        firstName: String,
        lastName: String,
        dateOfBirth: Date
    ) = map {
        mapNonNulls(
            this.firstName with firstName,
            this.lastName with lastName,
            this.dateOfBirth with dateOfBirth
        )
    }
}
```

### Usage Example

Here's a demonstration of constructing a new Person DataObject, validating it, and creating a new key to represent it.

```kotlin
val johnSmith = Person(
    firstName = "John",
    lastName = "Smith",
    dateOfBirth = Date(2017, 12, 5)
)

// Validate the object, which will throw a PropertyValidationUmbrellaException if it's invalid
// In this case, since there's no validation on the PropertyDefinitions, validation will succeed
Person.validate(johnSmith) 

// Because no key definition was defined this model will return a UUID based key
val key = Person.key(johnSmith)
```

## Basic DataModels
The basic data models form the foundation for defining data structures. DataModels consist of properties and can be
validated. With the exception of RootDataModels, they can be nested within other DataModels to group data more 
specifically. For example, address details can be stored within a Person DataModel.

If you define the model using Kotlin, any DataModel should extend from the DataModel class.

** Maryk Yaml example **

```yaml
name: Address
? 1: streetName
: !String
? 2: city
: !String
? 3: zipCode
: !String
```

## RootDataModel
A RootDataModel is essential for the storage of all DataModel structures, as it serves as
the root element. This model has additional methods for creating a unique key that is based
on the data within the object. For more information about keys, refer to the [key page](key.md).

Above example uses a RootDataModel.

## ValueDataModel
ValueDataModels are designed to store objects in a more compact and efficient manner
compared to regular DataModels. They are treated as values rather than objects, and this
allows them to be used in a variety of ways.

Some of the key advantages of using ValueDataModels include:

- They can be used as keys in maps, allowing for fast and efficient data retrieval.
- They can be used as values in lists, making it easier to store and manage multiple values.
- They can be indexed with multiple values at the same time, making it easier to manage large amounts of data.

ValueDataModels are constructed from properties that can be represented by a fixed number of bytes. 
This means that simple properties such as integers, booleans, dates, times, and floating-point numbers can be used, 
but more complex properties like strings and flexible bytes cannot. Additionally, ValueDataModels cannot 
contain more complex properties like sets, lists, and maps, as this would increase their size and make them
less efficient.

To give an example, consider a simple ValueDataModel representing a period by begin and end date. The properties in this 
model could include the start and end date, all represented by fixed-size dates. This model could then be used as a key in a map
to quickly retrieve information related to that period.

###Example:
 
**Maryk Yaml Description:**
```yaml
name: PersonRoleInPeriod
? 1: person
: !Reference
  dataModel: Person
? 2: role
: !Enum
  dataModel: Role
? 3: startDate
: !Date
? 4: endDate
: !Date
```
 
```yaml
// Role.enum.yml
cases:
  Admin: 1
  Moderator: 2
  User: 3
```
 
**Kotlin description** 

```kotlin
sealed class Role(index: Int): IndexedEnumImpl<Role>(index) {
    object Admin: Role(1)
    object Moderator: Role(2)
    object User: Role(3)
    
    companion object: IndexedEnumDefinition<Role>(Role::class, { arrayOf(Admin, Moderator, User) })
}

 
data class PersonRoleInPeriod(
    val person: Person,
    val role: Role,
    val startDate: Date,
    val endDate: Date
) : ValueDataObject(toBytes(person, role, startDate, stopDate)) {
    object Properties : ObjectProperties<PersonRoleInPeriod>() {
        val person by reference(1u, dataModel = { Person })
        val role by enum(2u, enum = Role)
        val startDate by date(3u)
        val endDate by date(4u)
    }

    companion object: ValueDataModel<TestValueObject, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestValueObject, Properties>) = TestValueObject(
            person = values(1u),
            role = values(2u),
            startDate = values(3u),
            endDate = values(4u)
        )
    }
}
```

## Creating Derived DataModels

It is not possible to extend DataModels in a traditional object-oriented programming sense, but it is possible to 
include properties of different types within a DataModel. This allows you to create a generic RootDataModel, such as a
Timeline, that can contain different varieties of DataModels.

To achieve this, you can create a MultiTypeDefinition that maps the different types of DataModels. The property that
holds the value will receive an additional type identifier. This type identifier can also be encoded into the key,
allowing for quick querying based on type.

**Example**

```kotlin
data class Post ...
data class Event ...
data class Advertisement ...

object TimelineItem: RootDataModel<TimelineItem>(
    keyDefinition = Multiple(
        Reversed(Properties.dateOfPosting),
        TypeId(Properties.item)
    ),
    properties = Properties
) {
    object Properties: PropertyDefinitions() {
        val dateOfPosting by dateTime(
            index = 1u,
            final = true,
            precision = TimePrecision.SECONDS
        )
        
        val item by multiType(
            index = 2u,
            final = true,
            typeMap = mapOf(
                1 to EmbeddedObjectDefinition(dataModel = Post),
                2 to EmbeddedObjectDefinition(dataModel = Event),
                3 to EmbeddedObjectDefinition(dataModel = Advertisement)
            )
        )
    }

    operator fun invoke(
        dateOfPosting: DateTime,
        item: TypedValue
    ) = values {
        mapNonNulls(
            this.dataOfPosting with dateOfPosting,
            this.item with item
        )
    }
}
```
