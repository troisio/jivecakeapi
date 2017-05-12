#### JiveCake API Project

#### Install

```sh
gradle clean shadowJar
```

#### Settings

An example `docker-compose.yml` for development, along with an `example-settings.yml` can be found in the `docker` directory

`rootOAuthIds`

The array of strings in the property `rootOAuthIds` of `example-settings.yml` are those auth0 user_ids which will have an 'ALL' permission for the root organization.
If you're doing work with a client application in conjunction with this project, you'll need to place the auth0 `user_id` of the user you want to log in as.
You can find these ids by going to your `auth0` account and looking up the users and viewing their JSON data.

This is mostly relevant for manual testing otherwise when you login to your client applications, the accounts you login with will be treated as normal users.

`apiToken`

The `apiToken` field in your settings yaml will need both the `user:update` and `user:read` scope from `auth0`

```sh
cd docker
docker build -t jivecakeapi
```

#### Run

```
cd docker
docker-compose up
```

### Testing

```sh
cd your_project_directory
gradle test -Ddb=MONGO_DB_URL
```

#### Things You Should Know

You will also need an [imgur](https://market.mashape.com/imgur) account from mashape. You will need to fill in the `mashape` portion of your configuration file.