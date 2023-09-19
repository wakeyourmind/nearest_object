# The nearest object

## Overview

Service is consuming records from 2 Kafka topics(`way` & `query_in`) and publishing the nearest one into the `query_out`
Kafka topic.
The nearest object is determined by finding a minimum distance between `query_in.x` and `way.distance`.

Since:   
*"The records are monotonically increasing by "distance" value.
The topic contains a large number of records, so it is not feasible to read and store them all in memory."*

It was decided to solve this condition by writing data to a long-term storage - relational database (`ClickHouse`).
Accordingly, records that are consumed from the `way` Kafka topic are stored in the database.
Based on the selected storage, the **nearest record** is found by reading and filtering records stored in the
database. The record consumed from the `query_in` Kafka topic remains as read input to find the
**nearest record** from the database - the found **nearest record** is published in the `query_out` Kafka topic. The
application communicates with the relational database using the HTTP interface.

To achieve better performance and efficiency in the case of inserting, reading, filtering, and current processing
approach
recommended to use a **Merge-Tree ClickHouse engine**.

For testing purposes was used:

```
create table if not exists way_record
(
distance Float64,
object String
) engine = MergeTree()
ORDER BY distance;
```

## How-To

### Run application with simulated Kafka topics and records

To run this application you will need:

1. to have installed **Docker** on your machine
2. run `docker/docker-compose.yml`
3. when all services are in a **running status**, please, use prepared bash script to simulate all Kafka staff:
   `docker exec <container_name> /bin/bash -c "/kafka-setup/init_setup.sh`
4. run the sbt task: `sbt run`
5. keep an eye on logs

### Testing

#### Integration tests

To run integration tests you will need:

1. to have installed Docker on your machine
2. run the sbt task: `sbt integrationTest`

#### Unit Tests

To run unit test you will need:

1. run the sbt task: `sbt unitTest`
