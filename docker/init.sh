#!/bin/bash

if [ -n "$PORT_FORWARD_URL" ] && [ -n "$PORT_FORWARD_USER" ]; then
  mkdir -p ~/.ssh
  ssh-keyscan $PORT_FORWARD_URL >> ~/.ssh/known_hosts
  ssh -f -N -L 27017:127.0.0.1:27017 $PORT_FORWARD_USER@$PORT_FORWARD_URL
fi

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