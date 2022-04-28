#!/bin/bash
cmd="./gradlew run --args=\"$1 $2\""
pushd getRevocationTime
eval $cmd
popd
