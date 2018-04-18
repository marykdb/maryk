# Maryk

Maryk is a way of defining rich data structures which enable you to create
data driven APIs for complex organizations. 

**Advantages of structuring data models in Maryk**

- Data models define validations so it is easy to validate data on all 
platforms.
- Data models provide an easy way to define a 
[key structure](documentation/key.md) which is ideal for NoSQL scans and 
fetches.
- All data are byte encodable into a storage and scan friendly format. 

This list is expanded as more functionality is ported and lands

## Projects

### Core
All core projects are multi-platform kotlin projects and support JS and JVM

- [core](core/README.md) - Contains the core Maryk classes, parsers and readers.
- [library](lib/README.md) - Contains all multi-platform utilities needed for core projects like Base64, String, Date, UUID and more
- [json](json/README.md) - A streaming JSON parser and writer
- [yaml](yaml/README.md) - A streaming YAML parser and writer

## Documentation

See the documentation for more details

- [DataModels](documentation/datamodel.md)
- [Properties](documentation/properties/properties.md)
