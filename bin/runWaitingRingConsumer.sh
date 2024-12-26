#!/bin/bash

EXPECTED_MESSAGES_TO_RECEIVE=${1:-100000}
SLEEP_TIME=${2:-5000000}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.BasicWaitingRingConsumer $EXPECTED_MESSAGES_TO_RECEIVE $SLEEP_TIME"

echo
echo $CMD
echo

$CMD

echo

