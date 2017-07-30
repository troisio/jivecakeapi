#!/bin/bash

if [ ! -d $SOURCE_DIRECTORY ]; then
  git clone -b $BRANCH --single-branch $REPOSITORY $SOURCE_DIRECTORY

  if [ ! -z $COMMIT ]; then
    cd $SOURCE_DIRECTORY
    git reset --hard $COMMIT
  fi
fi

cd $SOURCE_DIRECTORY

if [ ! -d $SOURCE_DIRECTORY/build ]; then
  gradle shadowJar
fi

java -jar $SOURCE_DIRECTORY/$(echo build/libs/*.jar) server $SETTINGS_FILE