# Properties

All [DataModels](../datamodel.md) define their structure by properties. Properties 
have a type like [Boolean](types/boolean.md), [Date](types/date.md), [String](types/string.md), 
[Enum](types/enum.md) and contain further properties that define how they are validated 
and stored.

## Types of properties

|Type                                     |Keyable |MapKey |MapValue |List   |Indexable   |
|:----------------------------------------|:------:|:-----:|:-------:|:-----:|:----------:|
|[String](types/string.md)                |false   |false  |true     |true   |true        |
|[Boolean](types/boolean.md)              |true    |true   |true     |true   |true        |
|[Number](types/number.md)*               |true    |true   |true     |true   |true        |
|[Enum](types/enum.md)                    |true    |true   |true     |true   |true        |
|[Date](types/date.md)                    |true    |true   |true     |true   |true        |
|[Time](types/time.md)                    |true    |true   |true     |true   |true        |
|[DateTime](types/datetime.md)            |true    |true   |true     |true   |true        |
|[Reference](types/reference.md)          |true    |true   |true     |true   |true        |
|[FixedBytes](types/fixedBytes.md)        |true    |true   |true     |true   |true        |
|[FlexBytes](types/flexBytes.md)          |false   |false  |true     |true   |true        |
|[MultiType](types/multiType.md)          |typeId**|false  |false    |false  |true        |
|[List](types/list.md)                    |false   |false  |false    |false  |true        |
|[Set](types/set.md)                      |false   |false  |false    |false  |true        |
|[Map](types/map.md)                      |false   |false  |false    |false  |key only    |
|[EmbeddedObject](types/embeddedObject.md)|false   |false  |true     |false  |subProp only|
|[ValueModel](types/valueModel.md)        |false   |true   |true     |true   |true        |

\* All numeric properties like Int8/16/32/64, UInt8/16/32/64, Float, Double 

\*\* Only the typeID of multitypes can be used in the key 


- Keyable - true for properties which can be used within a key. 
            (Only fixed byte length types can be used in key)
            Key properties must be required and final.
- MapKey - true for properties which can be used as a key within a Map
- MapValue - true for properties which can be used as a value within a Map
- List - true for properties which can be used within a List

## Operations

Properties can be checked or changed with operations like change, delete or
check. Read more in the [operations documentation](operations.md)

## References
For filters, order and validation exceptions it is needed to refer
to which property to operate on/report on. To refer you use 
[`property references`](references.md)

## Validation

All property definitions contain ways to add validations. These validations
can be things like making a property required, has a minimum value or
length or specific validations like a regex for a String property.

Check the page for each type of property to see what is possible.

### Validation present on all properties:

* Required - Property needs to be set. Default = true
* Final - Property can never be changed. Default = false
* Default - Sets the default value. Default = null

### Validation present depending on the type:

* Unique - Another field with this value is not allowed to exist. Useful 
for unique external IDs and email addresses.
* Minimum value - Property cannot have a value less than this value
* Maximum value - Property cannot have a value more than this value
* Minimum size - Property cannot be smaller than this size
* Maximum size - Property cannot be larger than this size
* Regular Expression - For string properties the content has to be a match

## Indexed

Most properties contain a property to set it on indexed. If this value is set 
to true it will be indexed in the persistent storage. Be careful adding indices
since this can slow down writing data. Also consider a good key design so less
indices are needed.

## Searchable

Most properties also contain a way to set if it is searchable which is on by default.
This value is read by search engines (like ElasticSearch) which can be used
on top of a persistence engine for more fuzzy and aggregated types of data retrieval. 
With this value you control what is stored in the index of that search engine.
