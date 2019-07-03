JUnit `TestRule` for declarative drools tests.  

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  

Specify rule names which are expected to be triggered for each use case using `@AssertRules` in addition to assertions inside test method.

	@DroolsSession(resources = {
			"classpath*:/org/droolsassert/rules.drl",
			"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
			"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
			ignoreRules = { "before", "after" })
	public class DroolsAssertTest {
	
		@Rule
		public DroolsAssert drools = new DroolsAssert();
	
		@Test
		@AssertRules("atomic int rule")
		public void testInt() {
			drools.insertAndFire(new AtomicInteger());
			assertEquals(1, drools.getObject(AtomicInteger.class).get());
		}
	
		@Test
		@AssertRules({ "atomic int rule", "atomic long rule" })
		public void testLong() {
			drools.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
			drools.fireAllRules();
			drools.assertFactsCount(3);
			assertEquals(2, drools.getObjects(AtomicLong.class).size());
		}
	
		@Test
		@AssertRules(expectedCount = { "atomic long rule", "2" }, ignore = "* int rule")
		public void testActivationCount() {
			drools.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
			assertEquals(2, drools.getObjects(AtomicLong.class).size());
		}
	
		@Test
		@AssertRules
		public void testNoRulesWereTriggered() {
			drools.insertAndFire(new BigDecimal(0));
			drools.assertFactsCount(1);
			assertEquals(0, drools.getObject(BigDecimal.class).intValue());
		}
	}

Rule under the test <a href="https://github.com/droolsassert/droolsassert/blob/master/src/test/resources/org/droolsassert/rules.drl">rules.drl</a>

**Example output**

	--> inserted: AtomicInteger[value=0]
	--> fireAllRules
	<-- 'before' has been activated by the tuple [AtomicInteger]
	before rules: 0
	<-- 'atomic int rule' has been activated by the tuple [AtomicInteger]
	<-- 'after' has been activated by the tuple [AtomicInteger]
	after rules: 1
	--> inserted: AtomicInteger[value=0]
	--> inserted: AtomicLong[value=0]
	--> inserted: AtomicLong[value=0]
	--> fireAllRules
	<-- 'before' has been activated by the tuple [AtomicLong]
	before rules: 0
	<-- 'before' has been activated by the tuple [AtomicLong]
	before rules: 0
	<-- 'before' has been activated by the tuple [AtomicInteger]
	before rules: 0
	<-- 'atomic int rule' has been activated by the tuple [AtomicInteger]
	<-- 'atomic long rule' has been activated by the tuple [AtomicLong]
	<-- 'atomic long rule' has been activated by the tuple [AtomicLong]
	<-- 'after' has been activated by the tuple [AtomicLong]
	after rules: 1
	<-- 'after' has been activated by the tuple [AtomicLong]
	after rules: 1
	<-- 'after' has been activated by the tuple [AtomicInteger]
	after rules: 1

**Example failure**

	java.lang.AssertionError: expected: [some other rule] unexpected: [atomic int rule]
		at org.junit.Assert.fail(Assert.java:88)
		at org.droolsassert.DroolsAssert.assertActivations(DroolsAssert.java:156)
		at org.droolsassert.DroolsAssert.awaitForActivations(DroolsAssert.java:186)
		at org.droolsassert.DroolsAssert.awaitForActivations(DroolsAssert.java:176)
		at org.droolsassert.DroolsAssert.evaluate(DroolsAssert.java:316)
		at org.droolsassert.DroolsAssert$1.evaluate(DroolsAssert.java:298)
		at org.junit.rules.RunRules.evaluate(RunRules.java:20)
		...
	
	java.lang.AssertionError: expected:<2> but was:<1>
		at org.junit.Assert.fail(Assert.java:88)
		at org.junit.Assert.failNotEquals(Assert.java:834)
		at org.junit.Assert.assertEquals(Assert.java:645)
		at org.junit.Assert.assertEquals(Assert.java:631)
		at org.droolsassert.DroolsAssertTest.testInt(DroolsAssertTest.java:26)
		...

**Version compatibility**  

For Drools 7.x use version 1.7.x  
For Drools 6.x use version 1.6.x  

**Latest maven builds**

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.7.0</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.6.0</version>
        <scope>test</scope>
    </dependency>
