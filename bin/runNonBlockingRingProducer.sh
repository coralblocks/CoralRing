#!/bin/bash

MESSAGES_TO_SEND=${1:-100000}
BATCH_SIZE_TO_SEND=${2:-100}
SLEEP_TIME=${3:-10000000}
WRITE_CHECKSUM=${4:-false}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.NonBlockingProducer $MESSAGES_TO_SEND $BATCH_SIZE_TO_SEND $SLEEP_TIME $WRITE_CHECKSUM"

echo
echo $CMD
echo

$CMD

echo

