#!/bin/bash

source ./bin/checkJvmVersion.sh

ADD_OPENS=""

if (( major_version > 8 )); then
    ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"
fi

CMD="java $ADD_OPENS -cp target/coralring-all.jar com.coralblocks.coralring.example.memory.SharedMemoryProducer"

echo
echo $CMD
echo

$CMD

echo

