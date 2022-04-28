#!/bin/bash
jsonFile=$(realpath $1)
cmd="./gradlew run --args=\"$jsonFile\""
pushd verify
eval $cmd
popd
