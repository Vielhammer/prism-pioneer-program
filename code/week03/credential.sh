#!/bin/bash
file=$(realpath $1)
pushd credential
./gradlew run
popd
