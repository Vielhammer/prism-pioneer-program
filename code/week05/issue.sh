#!/bin/bash
seedFile=$(realpath $1)
hashFile=$(realpath $2)
cmd="./gradlew run --args=\"$seedFile $hashFile $(pwd)\""
pushd issue
eval $cmd
popd
