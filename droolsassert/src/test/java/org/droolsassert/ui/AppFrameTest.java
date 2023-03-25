package org.droolsassert.ui;

import static java.util.Arrays.asList;
import static org.droolsassert.jbehave.DroolsSessionProxy.newDroolsSessionProxy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsSession;
import org.droolsassert.jbehave.DroolsSessionProxy;
import org.droolsassert.listeners.DroolsassertListener;
import org.droolsassert.listeners.StateTransitionBuilder;
import org.junit.Ignore;
import org.junit.Test;

public class AppFrameTest {

	private StateTransitionBuilder stb;

	@Test
	@Ignore("for manual run")
	public void test() {
		DroolsSession proxy = newDroolsSessionProxy(new DroolsSessionProxy() {
			{
				resources = new String[] { "file:src/test/resources/org/droolsassert/rules.drl" };
				showStateTransitionPopup = true;
			}
		});
		DroolsAssert droolsAssert = new DroolsAssert() {
			@Override
			protected List<DroolsassertListener> listeners() {
				stb = new StateTransitionBuilder(proxy, this, clock);
				return asList(stb);
			}
		};
		droolsAssert.init(proxy, null);
		stb.enabled();
		stb.beforeScenario("test", "scenario");
		droolsAssert.insertAndFire(new AtomicInteger());
		stb.afterScenario();
	}
}
