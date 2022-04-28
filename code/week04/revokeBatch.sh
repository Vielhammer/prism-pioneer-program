#!/bin/bash
seedFile=$(realpath $1)
oldHashFile=$(realpath $2)
cmd="./gradlew run --args=\"$seedFile $oldHashFile $3\""
pushd revokeBatch
eval $cmd
popd
