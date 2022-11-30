# TIS Trainee User Management

## About

This service provides user management and support functionality for TIS
Self-Service.

## Developing

### Running

```shell
gradlew bootRun
```

#### Pre-Requisites

- A Cognito instance to connect to.

#### Environmental Variables

| Name                 | Description                                   | Default |
|----------------------|-----------------------------------------------|---------|
| AWS_REGION           | The AWS region to use.                        |         |
| COGNITO_USER_POOL_ID | The ID of the Cognito user pool to manage.    |         |
| SENTRY_DSN           | A Sentry error monitoring Data Source Name.   |         |
| SENTRY_ENVIRONMENT   | The environment to log Sentry events against. | local   |

#### Usage Examples

##### Get User Account Details

```
GET /user-management/api/user-account/details/{username}
```

### Testing

The Gradle `test` task can be used to run automated tests and produce coverage
reports.
```shell
gradlew test
```

The Gradle `check` lifecycle task can be used to run automated tests and also
verify formatting conforms to the code style guidelines.
```shell
gradlew check
```

### Building

```shell
gradlew bootBuildImage
```

## Versioning

This project uses [Semantic Versioning](semver.org).

## License

This project is license under [The MIT License (MIT)](LICENSE).
