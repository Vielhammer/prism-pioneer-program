#!/bin/bash
pushd ../week03/getDidDocument
./gradlew run --args=\"$1\"
popd
