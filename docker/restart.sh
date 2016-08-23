#!/bin/bash

REMOVE="$(docker-compose --project-name "jivecakeapi" ps -q)"

if [ -n "$REMOVE" ]; then
    docker-compose --project-name jivecakeapi stop
    docker-compose --project-name jivecakeapi rm --force
fi

docker-compose --project-name jivecakeapi up