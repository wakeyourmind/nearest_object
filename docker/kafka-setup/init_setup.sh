#!/bin/bash

# Init topics
kafka-topics --create --topic way --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092
kafka-topics --create --topic query_in --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092
kafka-topics --create --topic query_out --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092

echo "Topics are created"

echo "Starting to produce records..."
(
  for ((i=1; i<=10000; i++)); do
    distance=$(awk "BEGIN {printf \"%.3f\", ($i * 0.001 * 2) + 0.001}")
    random_text=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 10 | head -n 1)
    echo "{\"distance\": $distance, \"object\": \"$random_text\"}" | kafka-console-producer --topic way --broker-list localhost:9092
  done
) &

(
  for ((i=1; i<=3000; i++)); do
    random_distance=$(awk -v min=-10000 -v max=10000 'BEGIN{srand(); printf "%.3f\n", (rand() * (max - min) + min) * 0.0001 + 0.001}')
    echo "{\"value\": $random_distance}" | kafka-console-producer --topic query_in --broker-list localhost:9092
  done
) &

wait

echo "Mock records are produced"

# Clean-up
kafka-topics --delete --topic way --bootstrap-server localhost:9092
kafka-topics --delete --topic query_out --bootstrap-server localhost:9092
kafka-topics --delete --topic query_in --bootstrap-server localhost:9092

echo "Clean-up is finished"