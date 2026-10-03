#!/bin/bash

MESSAGES_TO_SEND=${1:-100000}
BATCH_SIZE_TO_SEND=${2:-100}
SLEEP_TIME=${3:-5000000}

JAVA_MAJOR=$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')

UNSAFE_OPTION=""

if [ "$JAVA_MAJOR" -ge 24 ]; then
    UNSAFE_OPTION="--sun-misc-unsafe-memory-access=allow"
fi

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

CMD="java $UNSAFE_OPTION $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.ring.BasicWaitingRingProducer $MESSAGES_TO_SEND $BATCH_SIZE_TO_SEND $SLEEP_TIME"

echo
echo $CMD
echo

$CMD

echo

