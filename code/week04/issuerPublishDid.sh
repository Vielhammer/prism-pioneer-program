#!/bin/bash
file=$(realpath $1)
pushd issuerPublishDid
./gradlew run --args=\"$file\"
popd
