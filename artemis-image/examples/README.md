###Examples

This directory contains examples of customising the image for particular use cases

####amqp_sasl_scram_test__etc
In this example, you can run the image with configuration that locks the broker down to a single user on a single predefined
queue called `TEST`. The necessary configuration overrides:
 - restricting the acceptor to AMQP/SASL-SCRAM
 - providing RBAC for queue TEST

are in:`./amqp_sasl_scram_test__etc/amqp_sasl_scram.properties`

To exercise this example, you need to choose a password for the pre-configured user 'A'.
With SASL_SCRAM the broker retains a salted representation of that value, but not the plain text value.

Register your chosen password by creating `./amqp_sasl_scram_test__etc/user` using mvn as follows:
 
 `$> mvn exec:exec -Dexample.pwd=<some value>`

To see the result, cat the generated user file to see the stored representation:

 `$> cat ./amqp_sasl_scram_test__etc/user`

You can then mount the `./amqp_sasl_scram_test__etc directory` as `/app/etc` for the container and initialize JAAS 
via the `java.security.auth.login.config` system property, which is passed to the JVM via the ENV `JDK_JAVA_OPTIONS` as follows:

 `$> podman run --name=artemis-amqp -dp 61616:61616 --env  JDK_JAVA_OPTIONS=-Djava.security.auth.login.config=/app/etc/login.config --privileged -v ./amqp_sasl_scram_test__etc:/app/etc localhost/target/activemq-artemis-image:<version>`

Execute the artemis producer/consumer command line tools to validate secure access to the TEST queue using AMQP
SASL-SCRAM with your chosen password via:

 `$> ./bin/artemis producer --protocol amqp --url amqp://localhost:61616 --user A --password <some value>`

 `$> ./bin/artemis consumer --protocol amqp --url amqp://localhost:61616 --user A --password <some value>`

####byoc__etc
This is an example of "Bring Your Own Config" or BYOC. The image will look for `/app/etc/broker.xml`. If that file exists
it will be treated as the broker xml configuration for the embedded broker. If your existing configuration is nicely
locked down or if you want to provide some custom defaults for your image, referencing an existing broker.xml makes sense.
Property files can still be used to augment the defaults or be used solely for more dynamic parts of configuration.

To exercise the example, `./byoc__etc directory` as `/app/etc` for the container as follows:

 `$> podman run --name=artemis-byoc -dp 61616:61616 --privileged -v ./byoc__etc:/app/etc localhost/target/activemq-artemis-image:<version>`

Peek at the broker logs to note the broker name 'byoc' configured from the broker.xml file

`$> podman logs artemis-byoc

Execute the artemis producer/consumer command line tools to validate, it behaves like the bare image:

 `$> ./bin/artemis producer --url tcp://localhost:61616`

 `$> ./bin/artemis consumer --url tcp://localhost:61616`

