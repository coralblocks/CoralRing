#!/bin/bash

shopt -s expand_aliases

alias java16='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk16-zulu /Library/Java/JavaVirtualMachines/Default'
alias java17='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk17-oracle /Library/Java/JavaVirtualMachines/Default'
alias java21='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk21-oracle /Library/Java/JavaVirtualMachines/Default'
alias java23='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk23-oracle /Library/Java/JavaVirtualMachines/Default'

java16
java -version

./bin/runBlockingRingProducer.sh &
./bin/runBlockingRingConsumer.sh

java17
java -version

./bin/runBlockingRingProducer.sh &
./bin/runBlockingRingConsumer.sh

java21
java -version

./bin/runBlockingRingProducer.sh &
./bin/runBlockingRingConsumer.sh

java23
java -version

./bin/runBlockingRingProducer.sh &
./bin/runBlockingRingConsumer.sh

java16

