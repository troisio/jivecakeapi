#!/bin/bash

if [ ! -d $SOURCE_DIRECTORY ]; then
  git clone -b $BRANCH --single-branch $REPOSITORY $SOURCE_DIRECTORY

  if [ ! -z $COMMIT ]; then
    cd $SOURCE_DIRECTORY
    git reset --hard $COMMIT
  fi
fi

cd $SOURCE_DIRECTORY/resource/jwt

rm -f *
ssh-keygen -N '' -t rsa -b 4096 -f jwtrs256-original.key
openssl rsa -in jwtrs256-original.key -pubout -outform PEM -out jwtrs256.key.pub
openssl pkcs8 -topk8 -inform PEM -in jwtrs256-original.key -out jwtrs256.key -nocrypt

cd $SOURCE_DIRECTORY

if [ ! -d $SOURCE_DIRECTORY/build ]; then
  gradle shadowJar
fi

java -jar $SOURCE_DIRECTORY/$(echo build/libs/*.jar) server $SETTINGS_FILE