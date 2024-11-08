#!/bin/bash

version_output=$(java -version 2>&1)

version_line=$(echo "$version_output" | head -n 1)

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

if [[ "$major_version" != "8"
        && "$major_version" != "11"
        && "$major_version" != "16"
        && "$major_version" != "17"
        && "$major_version" != "19"
        && "$major_version" != "20"
        && "$major_version" != "21"
        && "$major_version" != "23"
    ]]; then
    echo
    echo "This Java version is not supported! => $major_version"
    echo
    exit
fi


