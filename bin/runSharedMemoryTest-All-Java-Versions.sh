#!/bin/bash

shopt -s expand_aliases

alias java17='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk17-oracle /Library/Java/JavaVirtualMachines/Default'
alias java21='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk21-oracle /Library/Java/JavaVirtualMachines/Default'
alias java23='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk23-oracle /Library/Java/JavaVirtualMachines/Default'

java17
java -version

./bin/runSharedMemoryTest.sh

java21
java -version

./bin/runSharedMemoryTest.sh

java23
java -version

./bin/runSharedMemoryTest.sh

