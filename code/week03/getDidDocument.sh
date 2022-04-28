#!/bin/bash
pushd getDidDocument
./gradlew run --args=\"$1\"
popd
