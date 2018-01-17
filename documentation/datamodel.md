# What is a DataModel?
DataModels describe the structure of the data. They contain 
[property definitions](properties/properties.md) which describe what type of data the 
property contains, how it is validated and other properties that are relevant to the 
storage. DataObjects are created from DataModels which can also validate or serialize
them.  

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

**Maryk Yaml Description:**
```yaml
properties:
  0 = firstName:
    type: String
  1 = lastName:
    type: String
  2 = dateOfBirth:
    type: Date
```

**Kotlin implementation.** Can be generated from Maryk Yaml Description
```kotlin
data class Person(
            val firstName: String,
            val lastName: String,
            val dateOfBirth: Date
){
    object Properties {
        val firstName = StringDefinition(name = "firstName", index = 0)
        val lastName = StringDefinition(name = "lastName", index = 1)
        val dateOfBirth = DateDefinition(name = "dateOfBirth", index = 2)
    }
    companion object: RootDataModel<Person>(definitions = listOf(
            Def(Properties.firstName, MarykObject::firstName)
            Def(Properties.lastName, MarykObject::lastName)
            Def(Properties.dateOfBirth, MarykObject::dateOfBirth)
    ))
}
```

### Usage
Below a new Person DataObject is being constructed and after it validated. Lastly 
it shows how to create a new key representing . 

```kotlin
val johnSmith = Person(
    firstName = "John",
    lastName = "Smith",
    dateOfBirth = Date(year=2017, month=12, day=5)
)

// Will throw a PropertyValidationUmbrellaException if invalid
// In this case there is no validation on the PropertyDefinitions so will succeed
Person.validate(johnSmith) 

// Because no key definition was defined this model will return a UUID based key
val key = Person.key.getKey(johnSmith)
```

## A generic DataModel
A generic DataModel contains properties and can be validated. They can be embedded
within other DataModels to contain more specific grouped data on an object. For example
it is possible to store address details below a Person DataModel. 

A generic DataModel extends from DataModel class. In Yaml you add ```embeddable = true```

** Maryk Yaml example **

```yaml
// Address.model.yml
properties
  0 = streetName:
    type: String
  1 = city:
    type: String
  2 = zipCode:
    type: String
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
// PersonRoleInPeriod.model.yml
properties:
  0 = person:
    type: Key<Person>
  1 = role:
    type: Enum<Role>
  2 = startDate:
    type: Date
  3 = endDate:
    type: Date
```
 
```yaml
// Role.enum.yml
values:
  Admin: 0
  Moderator: 1
  User: 2
```
 
**Kotlin description** 

```kotlin
enum class Role(override val index: Int): IndexedEnum<Option> {
    Admin(0), Moderator(1), User(2)
}

 
data class PersonRoleInPeriod(
        val person: Person,
        val role: Role,
        val startDate: Date,
        val endDate: Date
) : ValueDataObject(toBytes(person, role, startDate, stopDate)) {
    object Properties {
        val person = ReferenceDefinition(name = "person", index = 0, dataModel = Person)
        val role = EnumProperty(name = "role", index = 1, values = Role.values())
        val startDate = DateDefinition(name = "startDate", index = 2)
        val endDate = DateDefinition(name = "endDate", index = 2)
    }

    companion object: ValueDataModel<TestValueObject>(
            definitions = listOf(
                    Def(Properties.person, TestValueObject::person),
                    Def(Properties.role, TestValueObject::role),
                    Def(Properties.startDate, TestValueObject::startDate),
                    Def(Properties.endDate, TestValueObject::endDate)
            )
    ) {
        override fun construct(values: Map<Int, Any>) = TestValueObject(
            person = values[0] as Key<Person>,
            role = values[1] as Role,
            startDate = values[2] as Date,
            endDate = values[3] as Date
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

data class TimelineItem(
            val item: TypedValue,
            val dateOfPosting: DateTime
){
    object Properties {
        val dateOfPosting = DateTimeDefinition(
            name = "dateOfPosting",
            index = 0,
            required = true,
            final = true,
            precision = TimePrecision.SECONDS
        )
        val item = MultiTypeDefinition(
            name = "item",
            index = 1,
            required = true,
            final = true,
            typeMap = mapOf(
                0 to SubModelDefinition(dataModel = Post),
                1 to SubModelDefinition(dataModel = Event),
                2 to SubModelDefinition(dataModel = Advertisement)
            )
        )
    }
    companion object: RootDataModel<TimelineItem>(
        keyDefinitions = definitions(
            Reversed(Properties.dateOfPosting),
            TypeId(Properties.item)
        ),
        definitions = listOf(
            Def(Properties.item, MarykObject::item)
        )
    )
}
```
