#!/bin/bash
seedFile=$(realpath $1)
oldHashFile=$(realpath $2)
newHashFile=$(realpath $3)
cmd="./gradlew run --args=\"$seedFile $oldHashFile $newHashFile\""
cd updateDid
eval $cmd
cd ..
