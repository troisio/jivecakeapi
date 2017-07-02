### JiveCake API Project

#### Build

```sh
docker build -t jivecakeapi --file docker/Dockerfile
```

#### Restart

```sh
gradle clean shadowJar
docker-compose --file docker/docker-compose.yml down
docker-compose --file docker/docker-compose.yml up
```

#### Testing

```sh
cd your_project_directory
gradle test -Ddb=MONGO_DB_URL
```

#### Google Cloud Platform

This project depends on Google Cloud Platform. You will need a Google Cloud Platform account. You will need to download [Cloud SDK](https://cloud.google.com/sdk/). The [authentication process](https://developers.google.com/identity/protocols/application-default-credentials) is outside the scope of this document.