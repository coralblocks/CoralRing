#!/bin/bash

ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"

JAVA_MAJOR=$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')

UNSAFE_OPTION=""

if [ "$JAVA_MAJOR" -ge 24 ]; then
    UNSAFE_OPTION="--sun-misc-unsafe-memory-access=allow"
fi

CMD="java $UNSAFE_OPTION $ADD_OPENS -cp target/classes:target/coralring-all.jar com.coralblocks.coralring.example.memory.SharedMemoryExample"

echo
echo $CMD
echo

$CMD

echo

