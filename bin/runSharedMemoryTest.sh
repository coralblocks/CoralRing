#!/bin/bash

# Step 1: Capture the Java version output
version_output=$(java -version 2>&1)

# Step 2: Extract the version line
version_line=$(echo "$version_output" | head -n 1)

# Step 3: Extract the major version number
if [[ $version_line =~ \"([0-9]+\.[0-9]+) ]]; then
    version_number="${BASH_REMATCH[1]}"
    if [[ $version_number == 1.* ]]; then
        # Java 8 and below
        major_version=$(echo $version_number | cut -d'.' -f2)
    else
        # Java 9 and above
        major_version=$(echo $version_number | cut -d'.' -f1)
    fi
else
    echo "Could not determine Java version."
    exit 1
fi

ADD_OPENS=""

# Step 4: Compare the major version and print "HELLO" if greater than 8
if (( major_version > 8 )); then
    ADD_OPENS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"
fi


CMD="java $ADD_OPENS -cp target/coralring-all.jar com.coralblocks.coralring.example.memory.SharedMemoryExample"

echo
echo $CMD
echo

$CMD

echo

