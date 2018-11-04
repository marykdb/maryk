# Generator JVM tests

This project tests Protobuf generation against a JVM ProtoBuf
library to check if it complies to the standard.

It is not possible to put it in the main generator project 
because the ProtoBuf gradle plugin cannot be used in a
multiplatform project.
