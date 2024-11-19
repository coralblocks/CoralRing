#!/bin/bash

SLEEP_TIME=${1:-1000000000}

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.PrintProgressBlockingProducer $SLEEP_TIME"

echo
echo $CMD
echo

$CMD

echo

