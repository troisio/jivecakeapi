### JiveCake API Project

#### Dependencies

- [gradle](https://gradle.org/install)
- [docker](https://www.docker.com)

#### Build

```sh
cd docker
docker build -t jivecakeapi .
```

#### Restart

```sh
gradle clean shadowJar && \
docker-compose --file docker/docker-compose.yml down && \
docker-compose --file docker/docker-compose.yml up
```

#### Testing

```sh
cd your_project_directory
gradle test -Ddb=MONGO_DB_URL
```

#### Settings

The settings files in docker/settings.yml need to be filled out.