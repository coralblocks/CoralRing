#!/bin/bash

EXPECTED_MESSAGES_TO_RECEIVE=${1:-100000}
SLEEP_TIME=${2:-5000000}
CONSUMER_INDEX=${3:-0}
NUMBER_OF_CONSUMERS=${4:-3}
DELETE_FILE=${5:-true}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.WaitingBroadcastConsumer $EXPECTED_MESSAGES_TO_RECEIVE $SLEEP_TIME $CONSUMER_INDEX $NUMBER_OF_CONSUMERS $DELETE_FILE"

echo
echo $CMD
echo

$CMD

echo

