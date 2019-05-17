JUnit rule for declarative drools tests

<pre>
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
		da.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
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
		da.assertFactsCount(0);
		assertEquals(0, da.getObjects(AtomicLong.class).size());
	}
}
</pre>

for a rule file <a href="https://github.com/droolsassert/droolsassert/blob/master/src/test/resources/org/droolsassert/rules.drl">rules.drl</a>

<pre>
<dependency>
	<groupId>org.droolsassert</groupId>
	<artifactId>droolsassert</artifactId>
	<version>1.0.0</version>
</dependency>
</pre>
