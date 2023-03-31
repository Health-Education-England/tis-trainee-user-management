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

| Name                          | Description                                             | Default   |
|-------------------------------|---------------------------------------------------------|-----------|
| AWS_REGION                    | The AWS region to use.                                  |           |
| AWS_XRAY_DAEMON_ADDRESS       | The AWS XRay daemon host.                               |           |
| COGNITO_DSP_CONSULTANT_GROUP  | The name of the Cognito user group for DSP consultants. |           |
| COGNITO_USER_POOL_ID          | The ID of the Cognito user pool to manage.              |           |
| ENVIRONMENT                   | The environment to log events against.                  | local     |
| PROFILE_HOST                  | The host of TIS-Profile service.                        | localhost |
| PROFILE_PORT                  | The port number of TIS-Profile service.                 | 8082      |
| REQUEST_QUEUE_URL             | The URL of sync request queue.                          |           |
| SENTRY_DSN                    | A Sentry error monitoring Data Source Name.             |           |

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
