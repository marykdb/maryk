# Versioning in Maryk

In Maryk, every value stored in an object is assigned a unique version number. This version enables efficient querying 
by only requesting updated data. Additionally, the data store can retain past versions of values, allowing for a complete
history of changes to be tracked.

# Querying for Specific Versions

Maryk provides the [`GetChanges`/`ScanChanges`](query.md#Scan/Get Changes) and [`GetUpdates`/`ScanUpdates`](query.md#Scan/Get Updates)
queries to allow users to retrieve specific versions of data. The Changes variant returns all changes ordered by object 
and then by versioned changes within the object. The Updates variant returns changes ordered based on when they occurred, 
regardless of object, and also handles hard deletes.

# Representation of a Version

A version in Maryk is represented as an unsigned 64-bit integer (8 bytes) that encodes a Hybrid Logical Clock (HLC). 
The HLC combines both a physical clock (a UNIX millisecond-based timestamp) and a logical clock (a counter) to ensure
that every update is assigned a unique and chronologically correct version number.

The upper 44 bits of the version represent the physical clock, while the lower 20 bits represent the hybrid clock. 
The logical clock has millisecond precision and a maximum value of June 23rd, 2527 at 08:20:44. With a maximum of 
1048575 possible ticks, the logical clock allows for a large number of events within the same millisecond.

For more information on Hybrid Logical Clocks, you can read the original paper introducing the concept:
https://cse.buffalo.edu/tech-reports/2014-04.pdf


