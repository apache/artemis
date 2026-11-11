# ActiveMQ 5 unit tests against ActiveMQ Artemis wrapper


This maven module is used to run ActiveMQ5 unit tests against
ActiveMQ Artemis broker.

The Artemis broker is 'wrapped' in BrokerService and the unit
tests are slightly modified.

Then run the tests simply do

```mvn -DskipActiveMQTests=false clean test```

It will kickoff the whole test suite.

