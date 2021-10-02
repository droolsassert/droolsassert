package org.droolsassert;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.drools.core.common.EqualityKey.JUSTIFIED;
import static org.drools.core.common.EqualityKey.STATED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.drools.core.common.InternalFactHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("org/droolsassert/logicalEvents.drl")
public class LogicalEventsTest extends DroolsAssert {
	
	@RegisterExtension
	public DroolsAssert droolsAssert = this;
	
	@BeforeEach
	public void before() {
		setGlobal("stdout", System.out);
	}
	
	@Test
	public void testPositiveValueWasNotIncrementedBecauseOfNegativeValues() {
		AtomicInteger positive = new AtomicInteger(1);
		
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		
		assertEquals(1, positive.get());
	}
	
	@Test
	public void testPositiveValueWasIncrementedBecauseNegativeValuesWereReset() {
		AtomicInteger positive = new AtomicInteger(1);
		
		AtomicInteger negative = new AtomicInteger(-1);
		insertAndFire(negative);
		advanceTime(1, SECONDS);
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		negative.addAndGet(2);
		update(negative);
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		advanceTime(1, SECONDS);
		insertAndFire(new AdminEvent("reset negative values"));
		
		assertEquals(2, positive.get());
		assertEquals(2, negative.get());
	}
	
	@Test
	@Disabled("https://issues.redhat.com/browse/DROOLS-5971")
	public void testUpdateLogicalEvent() {
		AtomicInteger positive = new AtomicInteger(1);
		
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		advanceTime(1, SECONDS);
		insertAndFire(new AdminEvent("update logical event"));
		
		assertEquals(1, positive.get());
	}
	
	@Test
	public void testDeleteLogicalEvent() {
		AtomicInteger positive = new AtomicInteger(1);
		
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		advanceTime(1, SECONDS);
		insertAndFire(new AdminEvent("delete logical event"));
		
		assertEquals(2, positive.get());
	}
	
	@Test
	@Disabled("https://issues.redhat.com/browse/DROOLS-6072")
	public void testStage() {
		AtomicInteger positive = new AtomicInteger(1);
		
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		advanceTime(1, SECONDS);
		InternalFactHandle fh = (InternalFactHandle) insertAndFire(new Event("negative integer")).get(0);
		// staging will set it's status to stated
		assertEquals(STATED, fh.getEqualityKey().getStatus());
		
		insertAndFire(new AdminEvent("reset negative values"));
		assertEquals(1, positive.get());
	}
	
	@Test
	@Disabled("https://issues.redhat.com/browse/DROOLS-6080")
	public void testUnstage() {
		AtomicInteger positive = new AtomicInteger(1);
		
		insertAndFire(new AtomicInteger(-1));
		advanceTime(1, SECONDS);
		insertAndFire(positive);
		advanceTime(1, SECONDS);
		InternalFactHandle fh = (InternalFactHandle) insertAndFire(new Event("negative integer")).get(0);
		assertEquals(JUSTIFIED, getFactHandles(Event.class, e -> e.getMessage().equals("negative integer")).get(0).getEqualityKey().getStatus());
		// assertEquals(STATED, fh.getEqualityKey().getStatus());
		delete(fh);
		// The justified set can be unstaged, now that the last stated has been deleted
		assertEquals(JUSTIFIED, getFactHandle(Event.class).getEqualityKey().getStatus());
		
		assertEquals(1, positive.get());
		insertAndFire(new AdminEvent("reset negative values"));
		assertEquals(2, positive.get());
	}
	
	public static class Event {
		private String message;
		
		public Event(String message) {
			this.message = message;
		}
		
		public String getMessage() {
			return message;
		}
		
		public void setMessage(String message) {
			this.message = message;
		}
		
		@Override
		public boolean equals(Object obj) {
			return reflectionEquals(this, obj, false);
		}
		
		@Override
		public int hashCode() {
			return reflectionHashCode(this, false);
		}
	}
	
	public static class AdminEvent {
		private String message;
		
		public AdminEvent(String message) {
			this.message = message;
		}
		
		public String getMessage() {
			return message;
		}
		
		public void setMessage(String message) {
			this.message = message;
		}
	}
}
