#!/bin/bash

java -version

echo

CMD="java --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED -cp target/coralring-all.jar com.coralblocks.coralring.example.memory.SharedMemoryExample"

echo $CMD
echo

$CMD

