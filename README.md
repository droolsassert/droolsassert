## Goal

Relieve Drools JUnit testing 

## Audience

The goal of unit testing is to isolate each part of the program and show that the individual parts are correct. You can achieve this easier when working with drools using this library. Be certain about the rules being activated and the facts retained in your session for a scenario you need.

## Approach

Unit test is about taking minimum piece of code and test all possible usecases defining specification. With integration tests your goal is not all possible usecases but integration of several units that work together. Do the same with rules. Segregate rules by business meaning and purpose. Simplest 'unit under the test' could be file with single or [high cohesion](https://stackoverflow.com/q/10830135/448078) set of rules and what is required for it to work (if any), like common DSL definition file or decision table. For integration test you could take meaningful subset or all rules of the system. 

## Usage

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  
Spring ant-like [PathMatchingResourcePatternResolver](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html) gives you robust tool to include functionality you want to test together or segregate.  

    @DroolsSession("**/project/rules/ruleUnderTest.drl")

Declare JUnit5 [extension](https://junit.org/junit5/docs/current/user-guide/#extensions) for the test ([rule](https://www.baeldung.com/junit-4-rules) for JUnit4)

    @RegisterExtension
    public DroolsAssert drools = new DroolsAssert();

Specify list of rules expected to be activated for a scenario with `@TestRules` annotation in addition to assertions inside test method and use other useful utilities to deal with the session.

    @Test
    @TestRules(expected = "atomic int rule")
    public void testInt() {
        drools.insertAndFire(new AtomicInteger());
        assertEquals(1, drools.getObject(AtomicInteger.class).get());
    }

## Examples

[Dummy assertions](https://github.com/droolsassert/droolsassert/wiki/Dummy-assertions)  
[Complex event processing](https://github.com/droolsassert/droolsassert/wiki/Complex-event-processing)  
[Spring integration test](https://github.com/droolsassert/droolsassert/wiki/Spring-integration-test)  
[Extend it with your application specific utilities](https://github.com/droolsassert/droolsassert/wiki/Extension-example)  
[Gather performance statistic](https://github.com/droolsassert/droolsassert/wiki/Performance-stats)  
[Activation report](https://github.com/droolsassert/droolsassert/wiki/Activation-report)  
[State transition report](https://github.com/droolsassert/droolsassert/wiki/State-transition-report)  
[Run tests in parallel](https://github.com/droolsassert/droolsassert/wiki/Parallel-run)  
[Jbehave integration](https://github.com/droolsassert/droolsassert/wiki/Jbehave-integration)  
[Jbehave Spring example](https://github.com/droolsassert/droolsassert/wiki/Jbehave-Spring-example)  
[Jbehave extention](https://github.com/droolsassert/droolsassert/wiki/Jbehave-extention)  
[JUnit vs jbehave](https://github.com/droolsassert/droolsassert/wiki/JUnit-vs-jbehave)  

## Maven dependency
    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>x.x.x</version>
        <scope>test</scope>
    </dependency>

## Compatibility matrix
<table style="border-spacing: 10px;">
    <tr><td><b>Drools</b></td><td><b>Junit5</b></td><td><b>Junit4</b></td><td><b>Java</b></td></tr>
    <tr><td><b>10.x.x</b></td><td>3.4.2</td><td>2.8.2</td><td>17+</td></tr>
    <tr><td><b>9.x.x</b></td><td>3.3.4</td><td>2.7.8</td><td>11+</td></tr>
    <tr><td><b>8.x.x</b></td><td>3.1.10</td><td>2.6.10</td><td>11+</td></tr>
    <tr><td><b>7.x.x</b></td><td>3.0.19</td><td>2.5.19</td><td>1.8+</td></tr>
    <tr><td><b>6.4+</b></td><td>2.4.12</td><td>2.3.13</td><td>1.8+</td></tr>
</table>

## Support
[Buy me a coffee](https://www.buymeacoffee.com/droolsassert)
