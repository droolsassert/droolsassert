JUnit `TestRule` for declarative drools tests.  

Specify any combination of rules you want to test in single session using `@DroolsSession`, `logResources` to see what was actually included.  

Specify rule names which are expected to be triggered for each use case using `@AssertRules` in addition to assertions inside test method.

**Dummy assertions example** for <a href="https://github.com/droolsassert/droolsassert/blob/master/src/test/resources/org/droolsassert/rules.drl">rules.drl</a>

	@DroolsSession(resources = {
			"classpath*:/org/droolsassert/rules.drl",
			"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
			"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
			ignoreRules = { "before", "after" },
			logResources = true)
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

---

**Logical events test** for <a href="https://github.com/droolsassert/droolsassert/blob/master/src/test/resources/org/droolsassert/temporalReasoning.drl">the rule</a>

	@DroolsSession("classpath:/org/droolsassert/logicalEvents.drl")
	public class LogicalEventsTest {
	
		@Rule
		public DroolsAssert drools = new DroolsAssert();
	
		@Before
		public void beforeClass() {
			drools.setGlobal("stdout", System.out);
		}
	
		@Test
		@AssertRules({
				"input call",
				"drop dial-up if callee is talking",
				"drop the call if caller is talking more than permitted time",
				"call in progress dropped",
				"input call dropped"
		})
		public void testDeclineAnyInputCallsIfCalleeIsTalking() {
			Dialing caller1Dial = new Dialing("11111", "22222");
			drools.insertAndFire(caller1Dial);
			drools.assertRetracted(caller1Dial);
			CallInProgress call = drools.getObject(CallInProgress.class);
			assertEquals("11111", call.callerNumber);
	
			drools.advanceTime(5, MINUTES);
			Dialing caller3Dial = new Dialing("33333", "22222");
			drools.insertAndFire(caller3Dial);
			drools.assertExists(caller3Dial);
	
			drools.advanceTime(5, SECONDS);
			drools.assertExists(call);
			drools.assertExists(caller3Dial);
	
			drools.advanceTime(5, SECONDS);
			drools.assertExists(call);
			drools.assertRetracted(caller3Dial);
	
			drools.advanceTime(1, HOURS);
			drools.assertRetracted(call);
			drools.assertRetracted(caller3Dial);
	
			drools.assertAllRetracted();
		}
	
		public static class Dialing {
			public String callerNumber;
			public String calleeNumber;
	
			public Dialing(String callerNumber, String calleeNumber) {
				this.callerNumber = callerNumber;
				this.calleeNumber = calleeNumber;
			}
		}
	
		public static class CallInProgress {
			public String callerNumber;
			public String calleeNumber;
	
			public CallInProgress(String callerNumber, String calleeNumber) {
				this.callerNumber = callerNumber;
				this.calleeNumber = calleeNumber;
			}
		}
	
		public static class CallDropped {
			public String number;
			public String reason;
	
			public CallDropped(String number, String reason) {
				this.number = number;
				this.reason = reason;
			}
		}
	}

**Output**

	--> inserted: LogicalEventsTest.Dialing[callerNumber=11111,calleeNumber=22222]
	--> fireAllRules
	<-- 'input call' has been activated by the tuple [Dialing]
	--> inserted: LogicalEventsTest.CallInProgress[callerNumber=11111,calleeNumber=22222]
	--> retracted: LogicalEventsTest.Dialing[callerNumber=11111,calleeNumber=22222]
	--> inserted: LogicalEventsTest.Dialing[callerNumber=33333,calleeNumber=22222]
	--> fireAllRules
	<-- 'drop dial-up if callee is talking' has been activated by the tuple [Dialing, CallInProgress]
	--> inserted: LogicalEventsTest.CallDropped[number=33333,reason=callee is busy]
	<-- 'input call dropped' has been activated by the tuple [CallDropped, Dialing]
	Dial-up 33333 dropped due to callee is busy
	--> retracted: LogicalEventsTest.Dialing[callerNumber=33333,calleeNumber=22222]
	--> retracted: LogicalEventsTest.CallDropped[number=33333,reason=callee is busy]
	<-- 'drop the call if caller is talking more than permitted time' has been activated by the tuple [CallInProgress]
	--> inserted: LogicalEventsTest.CallDropped[number=11111,reason=call timed out]
	<-- 'call in progress dropped' has been activated by the tuple [CallDropped, CallInProgress]
	Call 11111 dropped due to call timed out
	--> retracted: LogicalEventsTest.CallInProgress[callerNumber=11111,calleeNumber=22222]
	--> retracted: LogicalEventsTest.CallDropped[number=11111,reason=call timed out]

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
