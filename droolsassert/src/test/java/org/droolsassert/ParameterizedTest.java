package org.droolsassert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DroolsSession(resources = {
		"classpath*:/org/droolsassert/rules.drl" },
		logResources = true)
public class ParameterizedTest {

	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();

	public static Stream<Arguments> data() {
		return Stream.of(
				Arguments.of(1, 1, 1),
				Arguments.of(2, 2, 4),
				Arguments.of(8, 2, 16),
				Arguments.of(4, 5, 20),
				Arguments.of(5, 5, 24));
	}

	@org.junit.jupiter.params.ParameterizedTest()
	@MethodSource("org.droolsassert.ParameterizedTest#data")
	@TestRules(expected = { "before", "after" })
	public void parameterized(int num1, int num2, int num3) {
		drools.insert(num1, num2, num3);
		drools.fireAllRules();
		assertTrue(drools.getObjects(Integer.class).size() > 0);
	}
}
