package edu.pitt.cs.people.guy.wind.benchmarks;

public class Results {
	

    // loop through all years
    private static void printMonthlyStatisticForTestingYears(String station,
							     HarvesterModel harvester,
							     String nameOfStatistic,
						HarvesterModel.MonthlyStatisticType monthlyStatistic){

		int arrayIndex = 1; // ignore first element in array
		
		for (int year = 2013; year <= 2014; year++) {

			System.out.print(nameOfStatistic + ":" +
					 station + ",&," + year + ",&,");		
			
			for (int month =1; month <=12; month++) {
				
				System.out.print(
				  harvester.getMinutesMonthlyStatistic(
								 arrayIndex,
								 year,
								 month,
								 monthlyStatistic) +
				  ",&,");		
				arrayIndex++;
				
			}
			
			System.out.println();
			
		}




    }



	public static void printResults(String station,
			HarvesterModel harvester,
			ElectricityPrice ep,
			int[] running_avg_size_minutes,
			int[] local_y_intercept_array) {
		
		// Print results
		double dNetNorm = harvester.getFractionOfEnergyHarvestedOverEnergyAvailable();
		double dMarketBalancePercentage =	ep.getMarketBalancePercentage();
		double dMarketNetNorm = dNetNorm * dMarketBalancePercentage;
		
		System.out.print("station,&,");
		
		for (int i=1; i<13; i++) {
			
			System.out.print("y_intercept_for_month_" + i + ",&," +
					"running_average_size_minutes_for_month_" + i + ",&,");

		}
		
		System.out.println("dNetNorm,&,dMarketBalancePercentage,&,dMarketNetNorm,");

		System.out.print("results: " + station + ",&,");
		for (int i=1; i<13; i++) {
			
			System.out.print(local_y_intercept_array[i] + ",&," +
			running_avg_size_minutes[i] + ",&,");

		}
		
		System.out.println(
				dNetNorm + ",&," +
				dMarketBalancePercentage + ",&," +
				dMarketNetNorm);
		

		printMonthlyStatisticForTestingYears(station, harvester, "monthly visible minutes",
		    HarvesterModel.MonthlyStatisticType.MINUTES_VISIBLE);

		printMonthlyStatisticForTestingYears(station, harvester,
"monthly minutes harvester between states",
      HarvesterModel.MonthlyStatisticType.MINUTES_HARVESTER_BETWEEN_STATES);


		
	}
	

	
	

	
	public static void printResultsFuzzy(String station,
			HarvesterModel harvester,
			ElectricityPrice ep,
			int[] running_avg_size_minutes,
			double[] deployment_membership_value_threshold) {
		
		// Print results
		double dNetNorm = harvester.getFractionOfEnergyHarvestedOverEnergyAvailable();
		double dMarketBalancePercentage =	ep.getMarketBalancePercentage();
		double dMarketNetNorm = dNetNorm * dMarketBalancePercentage;
		
		System.out.print("station,&,");
		
		for (int i=1; i<13; i++) {
			
			System.out.print("deployment_membership_value_threshold_array_for_month_" + i + ",&," +
					"running_average_size_minutes_for_month_" + i + ",&,");

		}
		
		System.out.println("dNetNorm,&,dMarketBalancePercentage,&,dMarketNetNorm,");

		System.out.print("results: " + station + ",&,");
		for (int i=1; i<13; i++) {
			
			System.out.print(deployment_membership_value_threshold[i] + ",&," +
			running_avg_size_minutes[i] + ",&,");

		}
		
		System.out.println(
				dNetNorm + ",&," +
				dMarketBalancePercentage + ",&," +
				dMarketNetNorm);
		

		printMonthlyStatisticForTestingYears(station, harvester, "monthly visible minutes",
						     HarvesterModel.MonthlyStatisticType.MINUTES_VISIBLE);
		
		printMonthlyStatisticForTestingYears(station, harvester,
						     "monthly minutes harvester between states",
						     HarvesterModel.MonthlyStatisticType.MINUTES_HARVESTER_BETWEEN_STATES);
		
		
		
	}
	
	
	
}
