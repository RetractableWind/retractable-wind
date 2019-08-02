package edu.pitt.cs.people.guy.wind.benchmarks;

import java.time.LocalDateTime;
import java.time.temporal.*;

public class DateStatistics {
	
	
	public static long getMinutesInMonthRemaining(LocalDateTime date) {
		
		
		LocalDateTime endDateTime  = LocalDateTime.of(date.getYear(), 
																				date.getMonth(), 
																				1, 0, 0);
		endDateTime  = endDateTime.plusMonths(1);
		
		// determine what the first minute of the next month is //
		return(ChronoUnit.MINUTES.between(date, endDateTime));
	}
		
		
		public static long getMinutesInMonthRemainingLessVisiblityMinutes(
				LocalDateTime date,
				HarvesterModel hm) {
			
			  return(getMinutesInMonthRemaining(date) - hm.getMinutesVisibleMonthly()); 
	
		
	}
		
		public static long getMinutesUntilTimeRemainingInMonthEqualsRemainingVisibilityAllocation(
				LocalDateTime date,
				HarvesterModel hm,
				Workload ws)  {			
			
			  return(getMinutesInMonthRemaining(date) -
					  hm.getRemainingAllocationOfVisiblityMinutes(ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)); 
			
		}

		public static double getMembershipValueForApproachingUseItOrLoseItPoint(LocalDateTime date,
				HarvesterModel hm,
				Workload ws)  {	
		
			double local_slope = -1.0/ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth;
			int local_y_intercept = 1;
			
			double candidateMembershipValue = local_slope * 
					getMinutesUntilTimeRemainingInMonthEqualsRemainingVisibilityAllocation(
							date,
							hm,
							ws) +
					local_y_intercept;
			
			if (candidateMembershipValue < 0) {
				
				candidateMembershipValue = 0;
				
			} else {
				
				if (candidateMembershipValue > 1) {
					
					candidateMembershipValue = 1;				
					
				}
			}
			
			return(candidateMembershipValue );
			
		}
	
}