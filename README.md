### JiveCake API Project

#### Install

```sh
gradle clean shadowJar
cd docker
docker build -t jivecakeapi
```

#### Google Cloud Platform

This project depends on Google Cloud Platform. You will need a Google Cloud Platform account. You will need to download [Cloud SDK](https://cloud.google.com/sdk/). The [authentication process](https://developers.google.com/identity/protocols/application-default-credentials) is outside the scope of this document.

#### Run

```sh
cd docker
docker-compose up
```

#### Testing

```sh
cd your_project_directory
gradle test -Ddb=MONGO_DB_URL
```