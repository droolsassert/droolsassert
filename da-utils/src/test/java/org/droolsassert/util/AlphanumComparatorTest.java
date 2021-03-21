package org.droolsassert.util;

import static java.util.Arrays.asList;
import static java.util.Collections.shuffle;
import static java.util.Collections.sort;
import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.junit.Test;

public class AlphanumComparatorTest {
	
	public static final List<String> list = asList(
			"1000a Radonius Maximus",
			"1000X Radonius Maximus",
			"10X Radonius",
			"200X Radonius",
			"20X Radonius",
			"20X Radonius Prime",
			"30X Radonius",
			"40X Radonius",
			"Allegia 50 Clasteron",
			"Allegia 500 Clasteron",
			"Allegia 50B Clasteron",
			"Allegia 51 Clasteron",
			"Allegia 6R Clasteron",
			"Alpha 001",
			"Alpha 2",
			"Alpha 200",
			"Alpha 2A",
			"Alpha 2A-8000",
			"Alpha 2A-900",
			"Callisto Morphamax",
			"Callisto Morphamax 500",
			"Callisto Morphamax 5000",
			"Callisto Morphamax 600",
			"Callisto Morphamax 6000 SE",
			"Callisto Morphamax 6000 SE2",
			"Callisto Morphamax 700",
			"Callisto Morphamax 7000",
			"Xiph Xlater 10000",
			"Xiph Xlater 2000",
			"Xiph Xlater 300",
			"Xiph Xlater 40",
			"Xiph Xlater 5",
			"Xiph Xlater 50",
			"Xiph Xlater 500",
			"Xiph Xlater 5000",
			"Xiph Xlater 58",
			null);
	
	public static final List<String> sorted = asList(
			"10X Radonius",
			"20X Radonius",
			"20X Radonius Prime",
			"30X Radonius",
			"40X Radonius",
			"200X Radonius",
			"1000a Radonius Maximus",
			"1000X Radonius Maximus",
			"Allegia 6R Clasteron",
			"Allegia 50 Clasteron",
			"Allegia 50B Clasteron",
			"Allegia 51 Clasteron",
			"Allegia 500 Clasteron",
			"Alpha 001",
			"Alpha 2",
			"Alpha 2A",
			"Alpha 2A-900",
			"Alpha 2A-8000",
			"Alpha 200",
			"Callisto Morphamax",
			"Callisto Morphamax 500",
			"Callisto Morphamax 600",
			"Callisto Morphamax 700",
			"Callisto Morphamax 5000",
			"Callisto Morphamax 6000 SE",
			"Callisto Morphamax 6000 SE2",
			"Callisto Morphamax 7000",
			"Xiph Xlater 5",
			"Xiph Xlater 40",
			"Xiph Xlater 50",
			"Xiph Xlater 58",
			"Xiph Xlater 300",
			"Xiph Xlater 500",
			"Xiph Xlater 2000",
			"Xiph Xlater 5000",
			"Xiph Xlater 10000",
			null);
	
	@Test
	public void logicTest() throws InterruptedException {
		shuffle(list);
		sort(list, new AlphanumComparator());
		assertArrayEquals(sorted.toArray(), list.toArray());
	}
}
