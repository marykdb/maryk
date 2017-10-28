## Serializing Maryk DataObjects

Maryk DataObjects can be serialized into two different formats: ProtoBuf and JSON.

### JSON
JSON has the advantage of being a widely adoped human readable format. For this 
reason it was included to more easily debug and to easily share data with third
parties. JSON can be outputted in pretty mode which includes more whitespace to 
further enhance readibility. The JSON is read in a streaming way for quicker 
results and less memory consumption.

### ProtoBuf
ProtoBuf was chosen because it is a widely adopted and very efficient byte
serialization format. The bytes can be read and written in a streaming way for 
faster parsing and less memory usage. [Read more here.](protobuf.md)