## The audience

If you find yourself writing too much boilerplate code to initialize drools sessions rather than writing actual test scenarios    
or it is rather hard to understand rules triggering order and cause-effect dependencies between the rules in your session,  
this library may help you spend less time debugging, make tests neat and support easier.  

## Usage

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  
Spring ant-like PathMatchingResourcePatternResolver gives you robust tool to include functionality you want to test together or segregate.  

	@DroolsSession(resources = {
			"classpath*:/org/droolsassert/rules.drl",
			"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
			"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
			ignoreRules = { "before", "after" },
			logResources = true)

Declare the rule for the test

	@Rule
	public DroolsAssert drools = new DroolsAssert();

Test which rules were triggered in declarative way with `@AssertRules` annotation in addition to assertions inside test method and use other useful utilities to deal with the session.

	@Test
	@AssertRules("atomic int rule")
	public void testInt() {
		drools.insertAndFire(new AtomicInteger());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
	}

## Examples    

[Dummy assertions](https://github.com/droolsassert/droolsassert/wiki/Dummy-assertions)  
[Logical events](https://github.com/droolsassert/droolsassert/wiki/Logical-events)

## Latest maven builds

For Drools 7.x  

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.7.4</version>
        <scope>test</scope>
    </dependency>

For Drools 6.x  

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.6.4</version>
        <scope>test</scope>
    </dependency>
