Temporal reasoning story

Scenario: definitions
Given import org.droolsassert.TemporalReasoningTest

Given drools session classpath:/org/droolsassert/temporalReasoning.drl

Given global stdout is System.out


Scenario: test regular heartbeat
Given new session for scenario
Given variable heartbeat1 is new Heartbeat(1)
When insert into MonitoringStream and fire heartbeat1
When advance time for 5 seconds
Then exist heartbeat1

Given variable heartbeat2 is new Heartbeat(2)
When insert into MonitoringStream and fire heartbeat2
Then exist heartbeat1, heartbeat2
When advance time for 5 seconds
Then deleted heartbeat1
Then exist heartbeat2

Given variable heartbeat3 is new Heartbeat(3)
When insert into MonitoringStream and fire heartbeat3
Then exist heartbeat2, heartbeat3
When advance time for 5 seconds
Then deleted heartbeat2
Then exist heartbeat3

Given variable heartbeat4 is new Heartbeat(4)
When insert into MonitoringStream and fire heartbeat4
Then exist heartbeat3, heartbeat4
When advance time for 5 seconds
Then deleted heartbeat3
Then exist heartbeat4

Then count of facts is 1
Given variable heartbeat as Heartbeat object from the session
Then assert heartbeat.ordinal is 4
Given facts printed

Then there were no activations


Scenario: test irregular heartbeat
Given new session for scenario
Given variable heartbeat1 is new Heartbeat(1)
When insert into MonitoringStream and fire heartbeat1
When advance time for 5 seconds
When advance time for 5 seconds

Given variable heartbeat2 is new Heartbeat(2)
Given variable heartbeat3 is new Heartbeat(3)
When insert into MonitoringStream and fire heartbeat2, heartbeat3
When advance time for 5 seconds

Then count of facts is 2
Given facts printed

Then count of all activations is 1 Sound the Alarm
