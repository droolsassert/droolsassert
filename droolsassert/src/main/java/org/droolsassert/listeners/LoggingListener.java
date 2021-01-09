package org.droolsassert.listeners;

import static java.lang.String.format;
import static java.lang.System.identityHashCode;
import static java.util.stream.Collectors.toList;
import static org.droolsassert.DroolsAssertUtils.getRuleActivatedBy;

import java.util.List;

import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsSession;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;

public class LoggingListener extends DefaultAgendaEventListener implements DroolsassertListener, RuleRuntimeEventListener {
	
	protected DroolsSession droolsSessionMeta;
	protected DroolsAssert droolsassert;
	
	public LoggingListener(DroolsSession droolsSessionMeta, DroolsAssert droolsassert) {
		this.droolsSessionMeta = droolsSessionMeta;
		this.droolsassert = droolsassert;
	}
	
	@Override
	public boolean enabled() {
		return droolsSessionMeta.log();
	}
	
	@Override
	public void beforeScenario(String test, String scenario) {
		droolsassert.log(test + "#" + scenario);
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		droolsassert.log(format("<-- '%s' activated by %s", event.getMatch().getRule().getName(), tupleToString(getRuleActivatedBy(event.getMatch()))));
	}
	
	@Override
	public void objectInserted(ObjectInsertedEvent event) {
		log("--> inserted", event.getObject());
	}
	
	@Override
	public void objectDeleted(ObjectDeletedEvent event) {
		log("--> retracted", event.getOldObject());
	}
	
	@Override
	public void objectUpdated(ObjectUpdatedEvent event) {
		log("--> updated", event.getObject());
	}
	
	protected void log(String action, Object fact) {
		droolsassert.log(format("%s #%s: %s", action, identityHashCode(fact), (droolsSessionMeta.logFacts() ? droolsassert.factToString(fact) : fact.getClass().getSimpleName())));
	}
	
	protected String tupleToString(List<Object> tuple) {
		return "" + tuple.stream().map(o -> format("%s#%s", o.getClass().getSimpleName(), identityHashCode(o))).collect(toList());
	}
}
