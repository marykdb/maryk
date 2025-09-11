# Versioning in Maryk

In Maryk, every value stored within an object is assigned a unique version number. This versioning system facilitates 
efficient querying by allowing users to request only the updated data. Furthermore, the data store can retain historical 
versions of values, enabling comprehensive tracking of changes over time.

## Querying for Specific Versions

Maryk offers two primary queries for retrieving specific versions of data: [`GetChanges`/`ScanChanges`](query.md#Scan/Get Changes) 
and [`GetUpdates`/`ScanUpdates`](query.md#Scan/Get Updates).

- **GetChanges/ScanChanges**: This query returns all changes ordered first by object and then by the versioned changes 
  within that object. It is useful for tracking modifications in a structured manner.

- **GetUpdates/ScanUpdates**: In contrast, this query returns changes ordered by the time they occurred, irrespective of 
  the object. It also effectively handles hard deletes, making it suitable for scenarios where understanding the 
  timeline of updates is crucial.

## Representation of a Version

A version in Maryk is represented as an unsigned 64-bit integer (8 bytes) that encodes a Hybrid Logical Clock (HLC). The 
HLC merges two components:

1. **Physical Clock**: This is a UNIX millisecond-based timestamp that provides the actual time of the update.
2. **Logical Clock**: This is a counter that ensures each update receives a unique and chronologically correct version 
   number.

The structure of the version is as follows:
- The upper 44 bits represent the physical clock.
- The lower 20 bits represent the logical clock.

The logical clock operates with millisecond precision and has a maximum value corresponding to June 23rd, 2527, at 
08:20:44. With a maximum of 1,048,575 possible ticks, the logical clock accommodates a substantial number of events 
occurring within the same millisecond.

For a deeper understanding of Hybrid Logical Clocks, you can refer to the original paper introducing the concept: 
[Hybrid Logical Clocks](https://cse.buffalo.edu/tech-reports/2014-04.pdf).
