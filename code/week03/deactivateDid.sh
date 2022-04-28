#!/bin/bash
seedFile=$(realpath $1)
oldHashFile=$(realpath $2)
cmd="./gradlew run --args=\"$seedFile $oldHashFile\""
pushd deactivateDid
eval $cmd
popd
