# What is a DataModel?
DataModels describe the structure of the data. They contain 
[property definitions](properties/properties.md) which describe what type of data the 
property contains, how it is validated and other properties that are relevant to the 
storage. Data Objects are created from DataModels which can also validate or serialize
these data objects.  

## Properties are identified by an index
To keep data transport and storage optimal all properties are required to
have an index integer besides a name to identify the people property. This index
must stay the same over the entire lifetime of the application. This index also
obviated the need of reflection in the code implementations.

### Example of a DataModel representing a Person in name and date of birth
The DataModel is contained within the companion object. It contains 3 references to the
property definitions. The Properties object is there for convenience so it is possible 
to get each definition in a type strict way. All is wrapped within a data class which 
can be instantiated. The data class itself should be immutable. The companion object
contains methods to validate the model. Because this model is a RootDataModel it also
contains methods to generate a key.

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

**Kotlin implementation.** Can be generated from Maryk Model YAML
```kotlin
object Person : RootDataModel<Person, Person.Properties>(
    properties = Properties
){ 
    object Properties: PropertyDefinitions() {
        val firstName = add(1, "firstname", StringDefinition(), Person::firstName)
        val lastName = add(2, "lastName", StringDefinition(), Person::lastName)
        val dateOfBirth = add(3, "dateOfBirth", DateDefinition(), Person::dateOfBirth)
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

### Usage
Below a new Person DataObject is being constructed and after it validated. Lastly 
it shows how to create a new key representing . 

```kotlin
val johnSmith = Person(
    firstName = "John",
    lastName = "Smith",
    dateOfBirth = Date(2017, 12, 5)
)

// Will throw a PropertyValidationUmbrellaException if invalid
// In this case there is no validation on the PropertyDefinitions so will succeed
Person.validate(johnSmith) 

// Because no key definition was defined this model will return a UUID based key
val key = Person.key(johnSmith)
```

## A generic DataModel
A generic DataModel contains properties and can be validated. They can be embedded
within other DataModels to contain more specific grouped data on an object. For example
it is possible to store address details below a Person DataModel. 

A generic DataModel extends from DataModel class. In Yaml you add ```embeddable = true```

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
All DataModel structures have a RootDataModel at the root to enable them to be stored.
The RootDataModel contains extra methods to create a key based on the data of the
object. Read further about keys on the [key page](key.md).
 
Above is an example of a RootDataModel

## ValueDataModel
Objects of ValueDataModels are stored in a different way compared to objects of the
normal DataModels. They are more like values and this gives them some unique 
 advantages.
 
 In contrary to normal DataModels they can be used as:
 - Keys of maps
 - Values in a list
 - Be indexed with multiple values at the same time
 
ValueDataModels are constructed by properties which can be represented by a fixed number
of bytes. This means that any simple property can be used except Strings and
FlexibleBytes. They cannot contain more complex properties like Sets, Lists and Maps as
this would make them lose their fixed amount of bytes.
 
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
? 4: endDate:
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
enum class Role(override val index: Int): IndexedEnum<Option> {
    Admin(1), Moderator(2), User(3)
}

 
data class PersonRoleInPeriod(
    val person: Person,
    val role: Role,
    val startDate: Date,
    val endDate: Date
) : ValueDataObject(toBytes(person, role, startDate, stopDate)) {
    object Properties : ObjectProperties<PersonRoleInPeriod>() {
        val person = add(1, "person", ReferenceDefinition(dataModel = Person), PersonRoleInPeriod::person)
        val role = add(2, "role", EnumProperty(values = Role.values()), PersonRoleInPeriod::role)
        val startDate = add(3, "startDate", DateDefinition(), PersonRoleInPeriod::startDate)
        val endDate = add(4, "endDate". DateDefinition(), PersonRoleInPeriod::endDate)
    }

    companion object: ValueDataModel<TestValueObject, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<TestValueObject, Properties>) = TestValueObject(
            person = values(1),
            role = values(2),
            startDate = values(3),
            endDate = values(4)
        )
    }
}
```

## Extending RootDataModels

DataModels cannot be extended in an OOP sense. But they can have properties which
can be of different types. This way it is possible to make a generic RootDataModel
like a Timeline which can contain different flavors of DataModels below. 

To accomplish this you create a MultiTypeDefinition mapping the different flavors 
of DataModels. The property containing the value gets an extra type id. This type id
can also be encoded into the key so data can be quickly queried on type. 

**Example**

```kotlin
data class Post ...
data class Event ...
data class Advertisement ...

object TimelineItem: RootDataModel<TimelineItem>(
    keyDefinitions = arrayOf(
        Reversed(Properties.dateOfPosting),
        TypeId(Properties.item)
    ),
    properties = Properties
) {
    object Properties: PropertyDefinitions() {
        val dateOfPosting = add(1, "dateOfPosting", DateTimeDefinition(
            final = true,
            precision = TimePrecision.SECONDS
        ))
        
        val item = add(2, "item", MultiTypeDefinition(
            final = true,
            typeMap = mapOf(
                1 to EmbeddedObjectDefinition(dataModel = Post),
                2 to EmbeddedObjectDefinition(dataModel = Event),
                3 to EmbeddedObjectDefinition(dataModel = Advertisement)
            )
        ))
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
