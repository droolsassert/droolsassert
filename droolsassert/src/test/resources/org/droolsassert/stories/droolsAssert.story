Drools assert story

Scenario: definitions
Given import java.util.concurrent.atomic

Given drools session 
    classpath*:/org/droolsassert/rules.drl
    classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}
    classpath*:/com/company/project/*/ruleUnderTest.rdslr
ignore rules: 'before', 'after'
log resources: true


Scenario: test int
Given new session for scenario
Given variable atomicInteger is new AtomicInteger()
When insert and fire atomicInteger
Then assert atomicInteger.get() is 1
Then there was single activation atomic int rule


Scenario: test long
Given new session for scenario
Given variable a1 is new AtomicInteger()
Given variable a2 is new AtomicLong()
Given variable a3 is new AtomicLong()
When insert facts a1, a2, a3
When fire all rules
Then count of facts is 3
Given variable listOfLong as AtomicLong objects from the session
Then assert listOfLong.size() is 2
Then all activations are 
    atomic int rule
    atomic long rule


Scenario: test activation count
Given new session for scenario, ignore '* int rule'
Given variable a1 is new AtomicInteger()
Given variable a2 is new AtomicLong()
Given variable a3 is new AtomicLong()
When insert and fire a1, a2, a3
Given variable listOfLong as AtomicLong objects from the session
Then assert listOfLong.size() is 2
Then count of all activations are 2 atomic long rule


Scenario: test expected source
Given new session for scenario
Given variable a1 is new AtomicInteger()
Given variable a2 is new AtomicLong()
Given variable a3 is new AtomicLong()
When insert facts a1, a2, a3
When fire all rules
Then count of facts is 3
Given variable listOfLong as AtomicLong objects from the session
Then assert listOfLong.size() is 2
Then all activations defined by org/droolsassert/expectedDroolsAssertTest.txt 


Scenario: test expected count source
Given new session for scenario, check scheduled, ignore source: **/ignoreDroolsAssertTest.txt
Given variable a1 is new AtomicInteger()
Given variable a2 is new AtomicLong()
Given variable a3 is new AtomicLong()
When insert facts a1, a2, a3
When fire all rules
Then count of facts is 3
Given variable listOfLong as AtomicLong objects from the session
Then assert listOfLong.size() is 2
Then count of all activations defined by **/expectedCountDroolsAssertTest.txt


Scenario: test no rule where triggered
Given new session for scenario
Given variable x is 'string'
When insert and fire x
Then count of facts is 1
Given variable xRef as String object from the session
Then assert xRef is 'string'
Given facts printed
Then there were no activations
