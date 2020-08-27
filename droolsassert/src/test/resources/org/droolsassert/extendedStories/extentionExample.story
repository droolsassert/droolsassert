Logical events story

Scenario: definitions
Given imports 
    org.droolsassert
    org.droolsassert.ComplexEventProcessingTest

Given drools session classpath:/org/droolsassert/complexEventProcessing.drl

Given global stdout is System.out


Scenario: test calls connect and disconnect logic stick to events
Given new session for scenario
Given variable exception is new RuntimeException('Something reported')
!-- When insert and fire exception
Given variable caller1Dial as new Dialing('11111', '22222')
When insert and fire caller1Dial
Then activated input call
Then retracted caller1Dial
Given variable call as CallInProgress object from the session
Then assert call.callerNumber equals '11111'

Given variable caller3Dial as Dialing from yaml {
    callerNumber: '33333',
    calleeNumber: '${caller1Dial.calleeNumber}'
}
When insert and fire caller3Dial
Then activated no rules
Then exist call, caller3Dial

Then no errors reported
!-- Then there are no scheduled activations
!-- Then retracted all facts