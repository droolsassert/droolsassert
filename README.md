## Goal

Relieve Drools JUnit testing 

## Audience

If you find yourself writing too much boilerplate code and it is hard to write neat and clear scenario specification for your rule    
or it is rather hard to understand rules triggering order and cause-effect dependencies between the rules in your session,  
this library will help you to build robust testing approach, make code cleaner and support easier.  

## Approach

Unit test is about taking minimum piece of code and test all possible usecases defining specification. With integration tests your goal is not all possible usecases but integration of several units that work together. Do the same with rules. Segregate rules by business meaning and purpose. Simplest 'unit under the test' could be file with single or [high cohension](https://stackoverflow.com/questions/10830135/what-is-high-cohesion-and-how-to-use-it-make-it) set of rules and what is required for it to work (if any), like common dsl definition file and decision table. For integration test you could take meaningful subset or all rules of the system. 

With this approach you'll have many isolated unit tests and few integration tests with limited amount of common input data to reproduce and test common scenarios. Adding new rules will not impact most of unit tests but few integration tests and will reflect how new rules impact common data flow.

## Usage

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  
Spring ant-like PathMatchingResourcePatternResolver gives you robust tool to include functionality you want to test together or segregate.  

	@DroolsSession(resources = {
		"classpath*:/org/droolsassert/rules.drl",
		"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
		"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
		logResources = true)

Declare the rule for the test

	@Rule
	public DroolsAssert drools = new DroolsAssert();

Test which rules were triggered in declarative way with `@TestRules` annotation in addition to assertions inside test method and use other useful utilities to deal with the session.

	@Test
	@TestRules(expected = "atomic int rule")
	public void testInt() {
		drools.insertAndFire(new AtomicInteger());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
	}

## Examples

[Dummy assertions](https://github.com/droolsassert/droolsassert/wiki/1.-Dummy-assertions)  
[Logical events](https://github.com/droolsassert/droolsassert/wiki/2.-Logical-events)  
[Spring integration test 1](https://github.com/droolsassert/droolsassert/wiki/3.-Spring-integration-test-1)  
[Spring integration test 2](https://github.com/droolsassert/droolsassert/wiki/4.-Spring-integration-test-2)  
[Extend it with your application specific utilities](https://github.com/droolsassert/droolsassert/wiki/5.-Extension-example)  
[Gather performance statistic](https://github.com/droolsassert/droolsassert/wiki/6.-Performance-stats)  
[Jbehave integration](https://github.com/droolsassert/droolsassert/wiki/8.1-Jbehave-integration)  
[Jbehave Spring example](https://github.com/droolsassert/droolsassert/wiki/8.2-Jbehave-Spring-example)  
[Jbehave extention](https://github.com/droolsassert/droolsassert/wiki/8.3-Jbehave-extention)  
[JUnit vs jbehave](https://github.com/droolsassert/droolsassert/wiki/8.4-JUnit-vs-jbehave)  

## Latest maven builds

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>2.0.3</version>
        <scope>test</scope>
    </dependency>
