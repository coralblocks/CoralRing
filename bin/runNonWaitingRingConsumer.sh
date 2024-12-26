#!/bin/bash

EXPECTED_MESSAGES_TO_RECEIVE=${1:-100000}
CHECK_CHECKSUM=${2:-false}
FALL_BEHIND_TOLERANCE=${3:-1.0}
SLEEP_TIME=${4:--1}
DELETE_FILE=${5:-true}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.BasicNonWaitingRingConsumer $EXPECTED_MESSAGES_TO_RECEIVE $CHECK_CHECKSUM $FALL_BEHIND_TOLERANCE $SLEEP_TIME $DELETE_FILE"

echo
echo $CMD
echo

$CMD

echo

