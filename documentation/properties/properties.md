# Properties

All [DataModels](../datamodel.md) define their structure by properties. Properties 
have a type like Boolean, Date, [String](string.md), Enum and contain further properties
that define how they are validated and stored.

# Types of properties

|Type                  |Keyable |MapKey |MapValue |List   |Indexable   |
|:---------------------|:------:|:-----:|:-------:|:-----:|:----------:|
|[String](string.md)   |false   |false  |true     |true   |true        |
|Boolean               |true    |true   |true     |true   |true        |
|[Number](number.md)*  |true    |true   |true     |true   |true        |
|Enum                  |true    |true   |true     |true   |true        |
|Date                  |true    |true   |true     |true   |true        |
|Time                  |true    |true   |true     |true   |true        |
|DateTime              |true    |true   |true     |true   |true        |
|Reference             |true    |true   |true     |true   |true        |
|FixedBytes            |true    |true   |true     |true   |true        |
|FlexBytes             |false   |false  |true     |true   |true        |
|List                  |false   |false  |false    |false  |true        |
|Set                   |false   |false  |false    |false  |true        |
|Map                   |false   |false  |false    |false  |key only    |
|MultiType             |typeId**|false  |false    |false  |true        |
|SubModel              |false   |false  |true     |false  |subProp only|
|ValueModel            |false   |true   |true     |true   |true        |

\* All numeric properties like Int8/16/32/64, UInt8/16/32/64, Float, Double 

\*\* Only the typeID of multitypes can be used in the key 


- Keyable - true for properties which can be used within a key. 
            (Only fixed byte length types can be used in key)
            Key properties must be required and final.
- MapKey - true for properties which can be used as a key within a Map
- MapValue - true for properties which can be used as a value within a Map
- List - true for properties which can be used within a List

# Validation

All property definitions contain ways to add validations. These validations
can be things like making a property required, has a minimum value or
length or specific validations like a regex for a String property.

Check the page for each type of property to see what is possible.

## Validation present on all properties:

* Required - Property needs to be set.
* Final - Property can never be changed.

## Validation present depending on the type:

* Unique - Another field with this value is not allowed to exist. Useful 
for unique external IDs and email addresses.
* Minimum value - Property cannot have a value less than this value
* Maximum value - Property cannot have a value more than this value
* Minimum size - Property cannot be smaller than this size
* Maximum size - Property cannot be larger than this size
* Regular Expression - For string properties the content has to be a match