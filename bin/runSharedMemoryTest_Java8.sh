#!/bin/bash

java -version

echo

CMD="java -cp target/coralring-all.jar com.coralblocks.coralring.example.memory.SharedMemoryExample"

echo $CMD
echo

$CMD

