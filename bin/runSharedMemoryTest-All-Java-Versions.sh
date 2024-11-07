#!/bin/bash

shopt -s expand_aliases

alias java8='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk8-oracle /Library/Java/JavaVirtualMachines/Default'
alias java11='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk11-zulu /Library/Java/JavaVirtualMachines/Default'
alias java16='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk16-zulu /Library/Java/JavaVirtualMachines/Default'
alias java17='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk17-oracle /Library/Java/JavaVirtualMachines/Default'
alias java19='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk19-oracle /Library/Java/JavaVirtualMachines/Default'
alias java20='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk20-oracle /Library/Java/JavaVirtualMachines/Default'
alias java21='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk21-oracle /Library/Java/JavaVirtualMachines/Default'
alias java23='sudo rm /Library/Java/JavaVirtualMachines/Default; sudo ln -sf /Library/Java/JavaVirtualMachines/jdk23-oracle /Library/Java/JavaVirtualMachines/Default'

java8

./bin/runSharedMemoryTest.sh

java11

./bin/runSharedMemoryTest.sh

java16

./bin/runSharedMemoryTest.sh

java17

./bin/runSharedMemoryTest.sh

java19

./bin/runSharedMemoryTest.sh

java20

./bin/runSharedMemoryTest.sh

java21

./bin/runSharedMemoryTest.sh

java23

./bin/runSharedMemoryTest.sh

java8

