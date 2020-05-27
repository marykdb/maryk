# Versioning

All values of an object which are stored are given a version. This version enables queries to only request the 
updated data. It is also possible for a data store to store all past versions of values. This enables queries 
into how values of objects were stored in the past and their whole change history.

# Querying

The [`GetChanges`/`ScanChanges`](query.md#Scan/Get Changes) and [`GetUpdates`/`ScanUpdates`](query.md#Scan/Get Updates) 
queries allow the user to query for specific versions of the data. The changes variant returns all changes ordered by
object and then the versioned changes within that object. The update query variant returns all changes ordered on when 
they happened regardless of object. The latter also handles hard deletes.

# Data representation of a version

A version is  an unsigned 64 bit integer (ULong of 8 bytes). It represents a Hybrid Logical Clock (HLC) which both encodes 
both a physical clock (A UNIX millisecond based time stamp) and a logical clock (A counter). This way if an update 
to data happens at the exact same millisecond, the counter of the logical clock can be highered. This way every update
is ensured to get its own unique version which is chronologically correct. 

More info can be read in the paper introducing the concept:
https://cse.buffalo.edu/tech-reports/2014-04.pdf

In Maryk the version is encoded as a HLC with the upper 44 bits representing the physical clock and the lower 20 bits
the hybrid clock. The logic clock has milliseconds precision and an upper bound at June 23rd, 2527 at 08:20:44.
The logic clock has 1048575 possible ticks which allows for a lot of events within the same millisecond.


