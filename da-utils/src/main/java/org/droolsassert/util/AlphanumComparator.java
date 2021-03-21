package org.droolsassert.util;

import static java.lang.Math.pow;

import java.util.Comparator;

public class AlphanumComparator implements Comparator<String> {
	
	private static char[] upperCaseCache = new char[(int) pow(2, 16)];
	private boolean nullIsLess;
	
	public AlphanumComparator() {
	}
	
	public AlphanumComparator(boolean nullIsLess) {
		this.nullIsLess = nullIsLess;
	}
	
	@Override
	public int compare(String s1, String s2) {
		if (s1 == s2)
			return 0;
		if (s1 == null)
			return nullIsLess ? -1 : 1;
		if (s2 == null)
			return nullIsLess ? 1 : -1;
		
		int i1 = 0;
		int i2 = 0;
		int len1 = s1.length();
		int len2 = s2.length();
		while (true) {
			// handle the case when one string is longer than another
			if (i1 == len1)
				return i2 == len2 ? 0 : -1;
			if (i2 == len2)
				return 1;
			
			char ch1 = s1.charAt(i1);
			char ch2 = s2.charAt(i2);
			if (isDigit(ch1) && isDigit(ch2)) {
				// skip leading zeros
				while (i1 < len1 && s1.charAt(i1) == '0')
					i1++;
				while (i2 < len2 && s2.charAt(i2) == '0')
					i2++;
				
				// find the ends of the numbers
				int end1 = i1;
				int end2 = i2;
				while (end1 < len1 && isDigit(s1.charAt(end1)))
					end1++;
				while (end2 != len2 && isDigit(s2.charAt(end2)))
					end2++;
				
				// if the lengths are different, then the longer number is bigger
				int diglen1 = end1 - i1;
				int diglen2 = end2 - i2;
				if (diglen1 != diglen2)
					return diglen1 - diglen2;
				
				// compare numbers digit by digit
				while (i1 < end1) {
					ch1 = s1.charAt(i1);
					ch2 = s2.charAt(i2);
					if (ch1 != ch2)
						return ch1 - ch2;
					i1++;
					i2++;
				}
			} else {
				ch1 = toUpperCase(ch1);
				ch2 = toUpperCase(ch2);
				if (ch1 != ch2)
					return ch1 - ch2;
				i1++;
				i2++;
			}
		}
	}
	
	private boolean isDigit(char ch) {
		return ch >= 48 && ch <= 57;
	}
	
	public char toUpperCase(char ch) {
		char cached = upperCaseCache[ch];
		if (cached == 0) {
			cached = Character.toUpperCase(ch);
			upperCaseCache[ch] = cached;
		}
		return cached;
	}
}