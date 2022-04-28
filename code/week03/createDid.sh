#!/bin/bash
file=$(realpath $1)
pushd ../week02/createDid
./gradlew run --args=\"$file\"
popd
