<div align="center">
  <img src="https://raw.githubusercontent.com/MorganaToken/morgana-core/main/themes/src/main/resources/theme/guinsoolab/welcome/resources/logo.png" width="120" alt="logo" />
  <br/>
  <small>An Open Source IAM solution for modern Applications and Services</small>
</div>

# Morgana

Morgana is an Open Source Identity and Access Management solution for modern Applications and Services.

This repository contains the source code for the Morgana Server, Java adapters and the JavaScript adapter.

![morgana-flow](https://raw.githubusercontent.com/MorganaToken/morgana-core/main/themes/src/main/resources/theme/guinsoolab/welcome/resources/morgana-flow.png)

## Help and Documentation

* [Quickstart](https://ciusji.gitbook.io/morgana/guides/getting-started)
* [Server Installation](https://ciusji.gitbook.io/morgana/guides/server-installation-and-configuration)
* [Securing Apps](https://ciusji.gitbook.io/morgana/guides/securing-applications-and-services)
* [Server Administration](https://ciusji.gitbook.io/morgana/guides/server-administration)
* [Server Developer](https://ciusji.gitbook.io/morgana/guides/server-developer)
* [Authorization Services](https://ciusji.gitbook.io/morgana/guides/authorization-services)
* [Upgrading](https://ciusji.gitbook.io/morgana/guides/upgrading)
* [REST APIs](https://ciusji.gitbook.io/morgana/apis/rest-apis)

## Reporting Security Vulnerabilities

If you've found a security vulnerability, please look at the [instructions on how to properly report it](https://github.com/MorganaToken/morgana-core/security/policy)


## Reporting an issue

If you believe you have discovered a defect in Keycloak, please open [an issue](https://github.com/MorganaToken/morgana-core/issues).
Please remember to provide a good summary, description as well as steps to reproduce the issue.


## Getting started

To run Morgana, download the distribution from our [website](https://ciusji.gitbook.io/morgana/guides/getting-started). Unzip and run:

    bin/kc.[sh|bat] start-dev

Alternatively, you can use the Docker image by running:

    docker run guinsoolab/morgana-core start-dev
    
For more details refer to the [Morgana Documentation](https://ciusji.gitbook.io/morgana/).


## Building from Source

To build from source, refer to the [building and working with the code base](docs/building.md) guide.


### Testing

To run tests, refer to the [running tests](docs/tests.md) guide.


### Writing Tests

To write tests, refer to the [writing tests](docs/tests-development.md) guide.


## Contributing

Before contributing to Keycloak, please read our [contributing guidelines](CONTRIBUTING.md).

## License

* [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
