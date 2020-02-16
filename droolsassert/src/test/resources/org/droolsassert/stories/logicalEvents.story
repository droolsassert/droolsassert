Logical events story

Given imports 
    org.droolsassert
    org.droolsassert.LogicalEventsTest

Given drools session classpath:/org/droolsassert/logicalEvents.drl

Given global stdout is System.out


!-- test calls connect and disconnect logic
Given new session for scenario

Given variable caller1Dial is new Dialing('11111', '22222')
When insert and fire caller1Dial
Then retracted caller1Dial
Given variable call as CallInProgress object from the session
Then assert call.callerNumber is '11111'

When advance time for 5 minutes
Given variable caller3Dial as new Dialing('33333', '22222')
When insert and fire caller3Dial
Then exist caller3Dial

When advance time for 5 seconds
Then exist call, caller3Dial

When advance time for 5 seconds
Then exist call
Then retracted caller3Dial

When advance time for 1 hours
Then retracted call

Then retracted all facts


!-- test calls connect and disconnect logic stick to events
Given new session for scenario

Given variable caller1Dial as new Dialing('11111', '22222')
When insert and fire caller1Dial
Then activated input call
Then retracted caller1Dial
Given variable call as CallInProgress object from the session
Then assert call.callerNumber equals '11111'

Given variable caller3Dial as new Dialing('33333', '22222')
When insert and fire caller3Dial
Then activated no rules
Then exist call, caller3Dial

When await for 'drop dial-up if callee is talking'
Then activated 'drop dial-up if callee is talking', 'input call dropped'
Then retracted caller3Dial

When await for 'drop the call if caller is talking more than permitted time'
Then count of activated are
    drop the call if caller is talking more than permitted time, 1
    call in progress dropped, 1
Then retracted call

Then there are no scheduled activations
Then retracted all facts


!-- test assert activations
Given new session for scenario
Given variable dial as Dialing from yaml {
    callerNumber: '11111',
    calleeNumber: '22222'
}
When insert and fire dial
Then all activations are 'input call'


!-- test assert scheduled activations
Given new session for scenario
Given variable dial as new Dialing('11111', '22222')
When insert and fire dial
Then all activations and scheduled are 
    input call
    drop the call if caller is talking more than permitted time
    call in progress dropped
