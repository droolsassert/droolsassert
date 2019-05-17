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
		public DroolsAssert da = new DroolsAssert();

		@Test
		@AssertRules("atomic int rule")
		public void testInt() {
			da.insertAndFire(new AtomicInteger());
			assertEquals(1, da.getObject(AtomicInteger.class).get());
		}

		@Test
		@AssertRules({ "atomic int rule", "atomic long rule" })
		public void testLong() {
			da.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
			da.fireAllRules();
			da.assertFactsCount(3);
			assertEquals(2, da.getObjects(AtomicLong.class).size());
		}

		@Test
		@AssertRules(expectedCount = { "atomic long rule", "2" }, ignore = "* int rule")
		public void testActivationCount() {
			da.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
			assertEquals(2, da.getObjects(AtomicLong.class).size());
		}

		@Test
		@AssertRules
		public void testNoRulesWereTriggered() {
			da.insertAndFire(new BigDecimal(0));
			da.assertFactsCount(1);
			assertEquals(0, da.getObjects(AtomicLong.class).size());
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


**Maven dependency**

    <dependency>
        <groupId>org.droolsassert</groupId>
        <artifactId>droolsassert</artifactId>
        <version>1.0.2</version>
        <scope>test</scope>
    </dependency>

**Version compatibility**  

For Drools 7.x use version 1.0.2 and higher  
For Drools 6.x use version 1.0.1  

