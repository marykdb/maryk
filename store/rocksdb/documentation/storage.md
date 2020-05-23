# RockDB record storage structure

# ColumnFamily structure

Each table is represented by multiple column families in which the actual data
is stored. It has at least a Model, Keys, Table, Index and a UniqueIndex column family and
if `keepAllVersions` is true on the store historic variants of those 3 storing 
the historical values. 

The column family descriptor is a byte array consisting 2 parts, the index of the
DB and a Type to indicate which of the types it is.

- The index is retrieved from the database map as passed to the RocksDBDataStore
- The type is created on init. At least Model, Keys, Table, Index and a UniqueIndex, 
  and if `keepAllVersions` is true also historic version of the same 3.
  - Model - index 0
  - Keys - index 1 
  - Table - index 2
  - Index - index 3
  - Unique - index 4
  - Historic Table - index 5 - Only if `keepAllVersions = true` on dataStore
  - Historic Index - index 6 - Only if `keepAllVersions = true` on dataStore
  - Historic Unique - index 7 - Only if `keepAllVersions = true` on dataStore

# Model
Stores the model used for the data. Useful to get current structure and to check if current data can be
read or updated with reference model from a client.

# Keys
Contains all the keys and creation dates for efficient scans for existence.

# Record structure
A record is stored in multiple key value pairs in multiple column families. Each 
Type stores data different for its use case.

# Table
A record is stored with the following structure within the Table column family. 

- KEY = VERSION. The root key-value pair is always the first version 
  indicating when the record was created. Is never changed afterwards.
- KEY:SOFT_DELETE_INDICATOR = VERSION Boolean. Indicates if the record was
  soft deleted. SoftDelete indicator is 0. Boolean is a value of 0 or 1.
- KEY:LAST_UPDATE_INDICATOR = VERSION. This pair stores the latest
  version which is stored and is always updated on any add, change, delete. 
  The indicator is `0b1000`
- KEY:REFERENCE = VERSION-VALUE. All values are stored with a key and property 
  reference. All values are prefixed with version so it is known when value was
  changed.
  
# Index
Index makes it easy to search records by values 

- INDEX_REFERENCE:VALUE:KEY = BOOLEAN. An indexed value and key which is prefixed by 
  index and stores the key so the record can be found by value. Contains key so multiple 
  references to same value can exist. BOOLEAN indicates if the index is set or unset.
  
# Unique
Unique stores a value which uniquely refers to a data record key. 

- INDEX_REFERENCE:VALUE = VERSION-KEY. An indexed value is prefixed by index and stores the
  key and version it was set so the record can be found by value. 
  
# Historic Table
This table stores all versions of values by appending the version to the reference. 

- KEY = VERSION. The root key-value pair is always the first version 
  indicating when the record was created. Is never changed afterwards.
- KEY:SOFT_DELETE_INDICATOR:VERSION = Boolean. Indicates if the record was
  soft deleted. SoftDelete indicator is 0. Boolean is a value of 0 or 1.
- KEY:REFERENCE:VERSION = VALUE. All values are stored with a key and property 
  reference and version.

# Historic Index
An index to search values at or before older versions.

- INDEX_REFERENCE:VALUE:KEY:VERSION = BOOLEAN. An indexed value and key is prefixed 
  by index so the record can be found by value.  Contains key so multiple 
  references to same value can exist. Stores the version so a unique value can be 
  searched at or before a version. BOOLEAN indicates if the index is set or unset.
  An old index value is always unset at same version when a new value is set.

# Historic Unique
Stores unique values by version 

- INDEX_REFERENCE:VALUE:VERSION = KEY. A versioned value to key pair for an index.
