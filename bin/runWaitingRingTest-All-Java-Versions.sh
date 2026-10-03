#!/bin/bash

shopt -s expand_aliases

platform="$(uname)"

if [ "$platform" = "Linux" ]; then
    # Linux
    alias java17='rm /usr/java/default; ln -sf /usr/java/java17 /usr/java/default'
    alias java21='rm /usr/java/default; ln -sf /usr/java/java21 /usr/java/default'
    alias java23='rm /usr/java/default; ln -sf /usr/java/java23 /usr/java/default'
    alias java25='rm /usr/java/default; ln -sf /usr/java/java25 /usr/java/default'
elif [ "$platform" = "Darwin" ]; then
    # macOS
    alias java17='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk17-oracle /Library/Java/JavaVirtualMachines/Default'
    alias java21='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk21-oracle /Library/Java/JavaVirtualMachines/Default'
    alias java23='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk23-oracle /Library/Java/JavaVirtualMachines/Default'
    alias java25='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk25-oracle /Library/Java/JavaVirtualMachines/Default'
else
    echo "ERROR: Unsupported operating system: $platform" >&2
    exit 1
fi

java17
java -version

./bin/runWaitingRingProducer.sh &
./bin/runWaitingRingConsumer.sh

java21
java -version

./bin/runWaitingRingProducer.sh &
./bin/runWaitingRingConsumer.sh

java23
java -version

./bin/runWaitingRingProducer.sh &
./bin/runWaitingRingConsumer.sh

java25
java -version

./bin/runWaitingRingProducer.sh &
./bin/runWaitingRingConsumer.sh
