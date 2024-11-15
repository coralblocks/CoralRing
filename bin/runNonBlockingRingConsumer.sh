#!/bin/bash

EXPECTED_MESSAGES_TO_RECEIVE=${1:-100000}

source ./bin/checkJvmVersion.sh

ADD_OPENS=""

if (( major_version > 8 )); then
    ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"
fi

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.NonBlockingConsumer $EXPECTED_MESSAGES_TO_RECEIVE"

echo
echo $CMD
echo

$CMD

echo

