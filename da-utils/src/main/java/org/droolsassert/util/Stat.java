package org.droolsassert.util;

import java.io.Serializable;

public interface Stat extends Serializable {
	String getDomain();
	
	long getLeapsCount();
	
	double getMinTimeMs();
	
	double getMinTimeSampleMs();
	
	double getMaxTimeMs();
	
	double getMaxTimeSampleMs();
	
	double getLeapTimeMs();
	
	double getAvgTimeMs();
	
	double getAvgTimeSampleMs();
	
	double getTotalTimeMs();
	
	long getFailedLeapsCount();
	
	long getPeersCount();
	
	void reset();
}