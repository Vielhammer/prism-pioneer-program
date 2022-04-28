#!/bin/bash
file=$(realpath $1)
cd createDid
./gradlew run --args=\"$file\"
cd ..
