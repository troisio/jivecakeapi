### Required Software

- gradle 3.2.1
- java 1.8
- docker 1.12

### Things You Should Know

Before working on this project, you should be familiar with [docker](https://www.docker.com), [mongoDB](https://www.mongodb.com), [git](https://git-scm.com/doc), [OAuth 2.0](https://oauth.net/2), HTTP, [Java](https://docs.oracle.com/javase/tutorial/java/) and using a linux terminal

You will also need an [auth0](https://auth0.com) account and will need to configure any clients if you are working with a client.

You will also need an [imgur](https://market.mashape.com/imgur) account from mashape. You will need to fill in the `mashape` portion of your configuration file.

### Settings

An example `docker-compose.yml` for development, along with an `example-settings.yml` can be found in the `docker` directory

`rootOAuthIds`

The array of strings in the property `rootOAuthIds` of `example-settings.yml` are those auth0 user_ids which will have an 'ALL' permission for the root organization. If you're doing work with a client application in conjunction with this project, you'll need to place the auth0 `user_id` of the user you want to log in as. You can find these ids by going to your `auth0` account and looking up the users and viewing their JSON data.

This is mostly relevant for manual testing otherwise when you login to your client applications, the accounts you login with will be treated as normal users.

`apiToken`

The `apiToken` field in your settings yaml will need both the `user:update` and `user:read` scope from `auth0`

### Running

Read `DOCKER.md` to read about how to build and run this project

### Testing

```sh
cd your_project_directory
gradle test -Ddb=MONGO_DB_URL
```

The database passed to the `db` system property will use and continually truncate a database named `test`