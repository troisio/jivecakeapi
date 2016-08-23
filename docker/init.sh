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

  gradle shadowJar
fi

if [ -d /root/tls ]; then
  mkdir /root/tls_copy
  cp --parents /root/tls_copy /root/tls/keystore.jks /root/tls_copy/keystore.jks

  if [ -a /root/tls/intermediate.crt ]; then
    keytool -import -trustcacerts -noprompt -alias root -file /root/tls/intermediate.crt -keystore /root/tls_copy/keystore.jks -storepass $KEY_STORE_PASSWORD
  fi

  keytool -import -trustcacerts -noprompt -alias server -file /root/tls/server.crt -keystore /root/tls_copy/keystore.jks -storepass $KEY_STORE_PASSWORD
fi

java -jar $SOURCE_DIRECTORY/$(echo build/libs/*.jar) server $SETTINGS_FILE