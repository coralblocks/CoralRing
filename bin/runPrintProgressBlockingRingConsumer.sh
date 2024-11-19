#!/bin/bash

SLEEP_TIME=${1:-1000000000}
IMPLY_FROM_FILE=${2:-false}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.PrintProgressBlockingConsumer $SLEEP_TIME $IMPLY_FROM_FILE"

echo
echo $CMD
echo

$CMD

echo

