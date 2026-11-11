# Database Tests

This module runs tests against selected Databases.

There is one profile for each supported Database:

- DB-hsql-tests
- DB-postgres-tests
- DB-mysql-tests
- DB-mssql-tests
- DB-db2-tests
- DB-oracle-tests

To enable the testsuite to run against any of these databases, simply enable the corresponding profiles.

## Providing Databases

When enabling a Database profile, you must download and provide the running database for that profile. You can also configure the JDBC URI.

You can refer to the examples provided under `./scripts`. Please note that you are responsible for complying with the licensing requirements of each database you provide.

## Configuring the JDBC URI

You can pass the JDBC URI as a parameter using the following supported parameters:

- `hsql.uri`
- `postgres.uri`
- `mysql.uri`
- `mssql.uri`
- `oracle.uri`
- `db2.uri`

Example:

```shell
mvn -Pdb2.uri='jdbc:db2://MyDB2Server:50000/artemis:user=db2inst1;password=artemis;'
```

#Security Authorization
Tests on this module will perform several `drop table` and `create table` operations. So the user used to connecto these databases must have such `security grants`.
Also It is recommended to the database schema allocated exclusively to this testsuite.

# Servers

One Artemis server is created for each supported database. After building, they will be available under ./target/${DATABASE}:

- `./target/hsql`
- `./target/postgres`
- `./target/mysql`
- `./target/mssql`
- `./target/db2`

Some of the tests on this module are connecting to the database directly, and these tests will use the JDBC jar directly from the `lib folder` from each of these servers.
