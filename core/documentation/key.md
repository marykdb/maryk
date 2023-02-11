# Keys in DataObjects

All DataObjects must be stored under a unique key, which acts as an index to the object.
The key serves as a permanent identifier for the object and can be used to retrieve it. 
If you store new data under an existing key, the existing data will be overwritten.

## Default UUID Keys

If you don't specify a key definition, the model will automatically generate 128-bit v4 UUID keys. 
These keys are guaranteed to be unique, but they won't provide any benefits for scanning the data.

## Choosing the Right Key from the Start

It's essential to be mindful when designing the key for a DataModel, as the
key structure cannot be changed after data has been stored (without complex migrations).
Consider the primary use case and ordering of the data and make sure the key is optimized
for this purpose. Secondary use cases can be addressed by adding an index.

## Properties for Key Structure

Key structures must have fixed byte lengths. This way the location of key elements are
predictable which are beneficial for scans over keys.

Properties that can be used 
for key elements include numbers, dates and times, fixed bytes, references, enums, booleans,
multi-type objects, and ValueDataModels containing similar values. 
Properties that cannot be used in keys include strings, flexible bytes, sets, lists, maps, and 
embedded models, as they have varying byte lengths.

## The order of keys
Keys are stored in order, which means that data scans will traverse or skip
the data in the same order. If the key starts with a reference, the data scan
can start at the exact location for that reference. 

If the data is often requested in the newest-first order, it is recommended to
reverse the date of creation so that new data is retrieved first.

## Tips on designing a key

- Consider performance: The key structure should be optimized for the most common use cases, as it will affect the performance of data retrieval and scans.
- If data "belongs" to a particular entity or person, start the key with a reference to that entity or person.
- If data needs to be ordered by time, include the time in the key. If newer data is frequently requested first, reverse the time.
- If the data has a primary multi-type property, include the type ID in the key so you can quickly retrieve data objects of a specific type. If time is also included in the key, make sure to place it after the type ID so that data is still ordered by time.
- If date precision in nanoseconds is somehow not enough, consider adding a random number to the key.
- Use indexing: If you have a key structure that is not optimized for your use case, you can use an index to improve performance. This is especially useful if you have large datasets.
