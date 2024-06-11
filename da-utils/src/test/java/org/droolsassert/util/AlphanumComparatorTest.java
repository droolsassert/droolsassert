package org.droolsassert.util;

import static java.util.Arrays.asList;
import static java.util.Collections.shuffle;
import static java.util.Collections.sort;
import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class AlphanumComparatorTest {

	public static final List<String> list = asList(
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
			"Alpha 0.000100",
			"Alpha 0.001",
			"Alpha 0.002 001",
			"Alpha 0.01",
			"Alpha 0.01 0001",
			"Alpha 1",
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
	public void logicTest() {
		List<String> bkp = new ArrayList<>(list);
		shuffle(list);
		sort(list, ALPHANUM_COMPARATOR);
		assertArrayEquals(bkp.toArray(), list.toArray());
	}
}
