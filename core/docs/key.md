# Keys in DataObjects

All DataObjects must be stored under a unique key, which acts as an index to the object. The key serves as a permanent
identifier for the object and can be used to retrieve it. If you store new data under an existing key, the existing data
will be overwritten.

## Default UUID Keys

If you don't specify a key definition, the model will automatically generate 128-bit v4 UUID keys. These keys are
guaranteed to be unique, ensuring that no two DataObjects will share the same identifier. However, please note that
while they are unique, they do not provide any inherent benefits for scanning or ordering the data.

## Choosing the Right Key from the Start

It's essential to be mindful when designing the key for a DataModel since the key structure cannot be changed after data
has been stored (without complex migrations). Consider the primary use case and the desired ordering of the data
carefully. Make sure the key is optimized for this purpose upfront. If secondary use cases arise, they can often be
addressed by adding an index later.

## Properties for Key Structure

Key structures must have fixed byte lengths. This predictability is crucial as it allows for efficient indexing and
scanning over keys.

Properties that can be utilized for key elements include numbers, dates and times, fixed bytes, references, enums,
booleans, multi-type objects, and ValueDataModels containing similar values.

On the other hand, properties that cannot be used in keys include strings, flexible bytes, sets, lists, maps, and
embedded models, as they possess varying byte lengths, which disrupts the consistency required for effective key
structures.

## The Order of Keys

Keys are stored in a specific order, meaning that data scans will traverse or skip the data in the same organized
manner. If the key begins with a reference, scans can efficiently start at the exact location corresponding to that
reference.

If the data is often requested in a newest-first order, it is advisable to reverse the date of creation in the key
structure. This way, newer data will be retrieved first during scans, enhancing the user experience.

## Tips on Designing a Key

- **Consider performance:** The key structure should be optimized for the most common use cases, as this will directly
  impact the performance of data retrieval and scans.
- **Entity association:** If data "belongs" to a particular entity or person, start the key with a reference to that
  entity or person. This associativity can streamline searches.
- **Time ordering:** If data needs to be ordered by time, include the time in the key. If newer data is frequently
  requested first, reverse the time to prioritize its retrieval.
- **Type identification:** If the data has a primary multi-type property, include the type ID in the key to enable quick
  retrieval of data objects of a specific type. Ensure that any time information follows the type ID to maintain
  chronological order.
- **Date precision:** If date precision in nanoseconds is insufficient for your application's needs, consider appending
  a random number to the key for additional uniqueness.
- **Use indexing:** If you find that your key structure is not optimized for your primary use case, utilize indexing to
  improve performance. This is especially beneficial when dealing with large datasets.
