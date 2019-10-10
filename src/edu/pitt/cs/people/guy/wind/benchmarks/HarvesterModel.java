package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;

/* updateStatistics must be called every minute */
class HarvesterModel {

//	NoiseAllowed noiseAllowed;
	
	private double dTotalEnergyHarvestedPrice = 0;
	
	public final int CUT_OUT_SPEED_KNOTS = 49; 
	public final int RECUT_IN_SPEED_KNOTS = 39; 
	
	public final int DESIGN_CONSIDERATION_MAXIMUM_AVG_WINDSPEED_KNOTS = 115; // assumed this high for 1st rev. 
	// since first revision of  data has speeds over 82 knots
	
	//public final int DESIGN_CONSIDERATION_MAXIMUM_AVG_WINDSPEED_KNOTS = 82; // Source: p22 of 
	  /// http://www.gov.pe.ca/photos/sites/envengfor/file/950010R1_V90-GeneralSpecification.pdf
	  /// "Max average wind **) 42.5 m/s" "**) 10 min., 50 years' mean wind speed"
	
	
	public final int TIME_TO_DEPLOY_MINUTES;
	public final int TIME_TO_RETRACT_MINUTES;
	private boolean bInCutOutRange = false;
	
	private float fDeploymentTimeRemaining = 0;
	private float fDeploymentTimeRemainingPrevious;
	private long minutesFullyStowedMonthly = 0;
	private long minutesVisibleMonthly = 0;
	private long minutesVisibleMonthlyPrevious = 0;
	private int minutesFullyDeployedMonthly = 0;
	private int minutesFullyDeployedMonthlyPrevious = 0;

	private long minutesInSimulationSincePreviousReset = 0;

        private int iVisibilityEventsMonthly = 0;
	
	public PowerCurve pc;
		
	private double dTotalEnergyAvailableKilowattMinute = 0;
	
	private double dTotalEnergyAvailableDuringQuietHoursKilowattMinute = 0;
	
	private double dTotalEnergyHarvestedKilowattMinute = 0;
	private double dTotalEnergyHarvestedKilowattMinuteThisMonth = 0;
	private double dTotalEnergyHarvestedKilowattMinuteMonthPrevious = 0;
	
	private double dTotalEnergyHarvestedKilowattMinutePrevious = 0;
	
	private boolean bInDeploymentModeVsRetractionMode = false; // Harvester is either retracting or deploying
	
	private int previousMonthValue = -1;
		
	ArrayList<MonthlyStatistics> listMonthlyStatistics = new ArrayList<MonthlyStatistics>(); 
	
	BufferedWriter bufferedWriterForLogging;

	
	public HarvesterModel(int localTimeToDeployMinutes, int localTimeToRetractMinutes) {

		File file;
		
//		try {
//			file = new File("minute-by-minute-log.csv");
//			
//			if (!file.exists()) {
//				
//				file.createNewFile();		
//				
//			}
//			
//			FileWriter fileWriter = new FileWriter(file);
//			bufferedWriterForLogging = new BufferedWriter(fileWriter);
//			
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		
		TIME_TO_DEPLOY_MINUTES = localTimeToDeployMinutes;
		TIME_TO_RETRACT_MINUTES = localTimeToRetractMinutes;
		fDeploymentTimeRemaining = TIME_TO_DEPLOY_MINUTES;
		fDeploymentTimeRemainingPrevious = fDeploymentTimeRemaining;

		final int HARVESTER_SIZE_SQM = 6362;

		System.out.println("HARVESTER_SIZE_SQM is " + HARVESTER_SIZE_SQM);
		pc = new PowerCurve(HARVESTER_SIZE_SQM);
		System.out.println("Wind power at 10 knots in kilowatts is " + pc.windPowerKilowattsViaTable(10, false));
		System.out.println("Wind power in-cut-out test " + pc.windPowerKilowattsViaTable(10, true));
		assert(pc.windPowerKilowattsViaTable(10, true) == 0);

		
	}

	public HarvesterModel(int localTimeToDeployMinutes, int localTimeToRetractMinutes, String station) {
		
		this(localTimeToDeployMinutes,  localTimeToRetractMinutes);
		
//		noiseAllowed = new NoiseAllowed(station);
		
	}
	
	
	// Positive values deploy.  Negative values retract
	// pass 1.0 for deployment full speed.  Pass 0.5 for half-speed
	// pass -1.0 for retraction full speed.  Pass -0.5 for half-speed
	public void movePerHarvesterDirectionValue(double harvesterDirectionValue) {
		
		fDeploymentTimeRemaining -= harvesterDirectionValue;		
		
		// adjust if time remaining is outside of limits
		if (fDeploymentTimeRemaining < 0) {
			
			fDeploymentTimeRemaining = 0; // fully deployed
			
		} else if (fDeploymentTimeRemaining > TIME_TO_DEPLOY_MINUTES) {
			
			fDeploymentTimeRemaining = TIME_TO_DEPLOY_MINUTES; // fully stowed
			
		}

		
	}
	
	// Put in deployment mode

	
	public void setMode() {
		
		bInDeploymentModeVsRetractionMode = true;
		
	}
	
	// Put in retraction mode
	public void resetMode() {
		
		bInDeploymentModeVsRetractionMode = false;
		
	}
	
	
	// pass 1.0 for full speed.  pass 0.5 for half-speed
	private void deploy(double fractionOfMaximumDeploymentSpeed) {

		// deploy if above or at threshold and time-of-day is not in restricted times
		if (fDeploymentTimeRemaining > 0) {
		
			fDeploymentTimeRemaining -= fractionOfMaximumDeploymentSpeed;
			
			// adjust if less than 0
			if (fDeploymentTimeRemaining < 0) {
				
				fDeploymentTimeRemaining = 0; // fully deployed
				
			} 
			
		}
	}

	private void retract(double fractionOfMaximumDeploymentSpeed) {

		if (fDeploymentTimeRemaining < TIME_TO_DEPLOY_MINUTES) {
			
			fDeploymentTimeRemaining += fractionOfMaximumDeploymentSpeed;
			
			// adjust if more than the total time to deploy
			if (fDeploymentTimeRemaining > TIME_TO_DEPLOY_MINUTES) {
				
				fDeploymentTimeRemaining = TIME_TO_DEPLOY_MINUTES; // fully retracted	
				
			}
		}
			
	}

	// This function must be called every minute of simulation
		public void processMode(Workload.WindspeedSample  sample, ElectricityPrice ep,
				boolean bRetractWhenInCutOutRange) {
			
			int windSpeedKnotsRaw = sample.windspeed_knots;
			
		// check if wind speed is too fast
		if (windSpeedKnotsRaw >= CUT_OUT_SPEED_KNOTS) {
			
			bInCutOutRange = true;
			
			if (windSpeedKnotsRaw > DESIGN_CONSIDERATION_MAXIMUM_AVG_WINDSPEED_KNOTS ) {
				
				System.out.println("Harvester model: Danger: windSpeedKnotsRaw value of " +
				windSpeedKnotsRaw + 
				" exceeds DESIGN_CONSIDERATION_MAXIMUM_AVG_WINDSPEED_KNOTS value of " +
				DESIGN_CONSIDERATION_MAXIMUM_AVG_WINDSPEED_KNOTS);
				
			}
			
			
		}
				
		if (bInCutOutRange) {
			
			//this.retract(1.0);  // commented out in order to not automatically retract in this condition			
			if (windSpeedKnotsRaw < RECUT_IN_SPEED_KNOTS) {
				
				bInCutOutRange = false;
				
			} else {
				
				if (bRetractWhenInCutOutRange) {
					this.retract(1.0);  
				}
				
			}
			
		} else {
			
			if (bInDeploymentModeVsRetractionMode) {
				
				this.deploy(1.0);
				
			} else {
				
				this.retract(1.0);
				
			}			
			
		}
		
		
		// if new month is starting, reset time statistics before updating
		if (sample.date.getMonthValue() != previousMonthValue) {

			//System.out.println("minutesVisibleMonthly:" + minutesVisibleMonthly);
			addToMonthlyStatisticsList(sample.date, 
						   minutesVisibleMonthly,
						   minutesFullyDeployedMonthly);
			minutesVisibleMonthlyPrevious = minutesVisibleMonthly;
			minutesFullyDeployedMonthlyPrevious = 
			    minutesFullyDeployedMonthly;
			resetMonthlyStatistics(ep);
			
		}
		previousMonthValue = sample.date.getMonthValue(); 
		
		
		updateStatistics(sample, ep);

		
	}
	
	
	public void addToMonthlyStatisticsList(LocalDateTime date,
					       long minutesVisibleMonthlyLocal,
					       int minutesFullyDeployedMonthlyLocal) {

		MonthlyStatistics ms = new MonthlyStatistics(date,
							     minutesVisibleMonthlyLocal,
						      minutesFullyDeployedMonthlyLocal);
	listMonthlyStatistics.add(ms);
		
	}

	public void addToMonthlyStatisticsListForFINALMonth() {
		
		MonthlyStatistics msPrevious = listMonthlyStatistics.get(listMonthlyStatistics.size() - 1);
		LocalDateTime datePrevious = LocalDateTime.of(msPrevious.year, msPrevious.month, 1, 0, 0, 0);
		LocalDateTime monthAfterFinalMonth = datePrevious.plusMonths(2);
		
		MonthlyStatistics ms = new MonthlyStatistics(monthAfterFinalMonth, minutesVisibleMonthly, minutesFullyDeployedMonthly);
		listMonthlyStatistics.add(ms);
		
	}
	
    boolean bFullyStowedStatePrevious = true;
    boolean bFullyStowedStateCurrent = true;

	private void updateStatistics(Workload.WindspeedSample  sample, ElectricityPrice ep) {

		int windSpeedKnots = sample.windspeed_knots;
		
		minutesInSimulationSincePreviousReset++;

		bFullyStowedStatePrevious = bFullyStowedStateCurrent;
		
		if (fDeploymentTimeRemaining == TIME_TO_DEPLOY_MINUTES) {
			
			minutesFullyStowedMonthly++;
			bFullyStowedStateCurrent = true;
			
		} else {
			
			minutesVisibleMonthly++;
			bFullyStowedStateCurrent = false;
						
		}

		// update visibility event count if new
		if (bFullyStowedStatePrevious && !bFullyStowedStateCurrent) {

		    iVisibilityEventsMonthly++;

		}

	// update fully deployed statistic
		if (fDeploymentTimeRemaining == 0)
		    {
			minutesFullyDeployedMonthly++;
		    }
		
		double windEnergyAvailableKilowattMinute = pc.windPowerKilowattsViaTable(windSpeedKnots, bInCutOutRange);
		
		if (fDeploymentTimeRemaining <= 0.0) {
			
			// record how much energy was harvested
			dTotalEnergyHarvestedKilowattMinute = dTotalEnergyHarvestedKilowattMinute +
					windEnergyAvailableKilowattMinute;
			dTotalEnergyHarvestedKilowattMinuteThisMonth+=windEnergyAvailableKilowattMinute;
					
		} 
		
		// if fDeploymentTimeRemaining decreased, then subtract the energy used to deploy
		if (fDeploymentTimeRemainingPrevious > fDeploymentTimeRemaining) {

			double deploymentEnergyUsedPerMinuteKilowattMinute =
			getDeploymentEnergyUsedPerMinuteKilowattMinute(TIME_TO_DEPLOY_MINUTES,
					windSpeedKnots);
			
			dTotalEnergyHarvestedKilowattMinute -= deploymentEnergyUsedPerMinuteKilowattMinute;
					
			dTotalEnergyHarvestedKilowattMinuteThisMonth-=
					deploymentEnergyUsedPerMinuteKilowattMinute;
			
		}
			
		fDeploymentTimeRemainingPrevious =  fDeploymentTimeRemaining;
		// record how much energy would have been harvested if the
		//  harvester were to have been permanently deployed
		dTotalEnergyAvailableKilowattMinute += windEnergyAvailableKilowattMinute;
		
//		if (!(noiseAllowed.bIsNoiseAllowed(sample.date))) {
//			
//			dTotalEnergyAvailableDuringQuietHoursKilowattMinute += windEnergyAvailableKilowattMinute;
//
//		}
		
		// record change for each minute
		double dTotalEnergyHarvestedKilowattMinuteChange =
				(dTotalEnergyHarvestedKilowattMinute - dTotalEnergyHarvestedKilowattMinutePrevious);
		
		ep.calculateAndStoreEnergyHarvestedCanadianDollars(sample.date, dTotalEnergyHarvestedKilowattMinuteChange);
		dTotalEnergyHarvestedKilowattMinutePrevious = dTotalEnergyHarvestedKilowattMinute;
		
		
//		//log - for verification: TODO remove or comment after verification
//		if (bHeadingNotPrinted) {
//
//			System.out.println("dTotalEnergyHarvestedKilowattMinute" + "," +
//					"fDeploymentTimeRemaining" + "," +
//					"windSpeedKnots");
//
//			bHeadingNotPrinted = false;
//		}
//
//		System.out.println(dTotalEnergyHarvestedKilowattMinute + "," +
//				(int) fDeploymentTimeRemaining + "," +
//				windSpeedKnots);

//	// Print to log.  Comment out if not using log.
//		try {
//			bufferedWriterForLogging.write(windSpeedKnots + "," +
//																	fDeploymentTimeRemaining + "," +
//																	 minutesVisibleMonthly +
//																	"\n");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		
	}
	
	Boolean bHeadingNotPrinted = true;// for verification: TODO remove  or comment after verification
	
	public double getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious() {

		return(dTotalEnergyHarvestedKilowattMinuteMonthPrevious);
	
	}
	
	public double getTotalEnergyHarvestedKilowattMinuteThisMonth() {
	
		return(dTotalEnergyHarvestedKilowattMinuteThisMonth);

	}
	
	public long getMinutesVisibleMonthly() {
		
		return(minutesVisibleMonthly);
		
	}

	public long getMinutesHarvesterIsBetweenStates() {
		
		return(minutesVisibleMonthly-minutesFullyDeployedMonthly);
		
	}
	
	public double getFractionOfEnergyHarvestedOverEnergyAvailable() {
		
		return (dTotalEnergyHarvestedKilowattMinute/dTotalEnergyAvailableKilowattMinute);
		
	}

	public double getFractionOfEnergyAvailableDuringQuietHoursOverEnergyAvailable() {
		
		return (dTotalEnergyAvailableDuringQuietHoursKilowattMinute/dTotalEnergyAvailableKilowattMinute);
		
	}

	
	
	public double getEnergyHarvestedKilowattHours() {
		
		return (dTotalEnergyHarvestedKilowattMinute/60);
		
	}
	
	public double getEnergyHarvestedAVAILABLEKilowattHours() {
		
		return (dTotalEnergyAvailableKilowattMinute/60);
		
	}

	public double getEnergyHarvestedAvailableDuringQuietHoursKilowattHours() {
		
		return (dTotalEnergyAvailableDuringQuietHoursKilowattMinute/60);
		
	}

        public int getVisibilityEventsMonthly() {
	
	    return (iVisibilityEventsMonthly);
		
	}

	
	public void resetMonthlyStatistics(ElectricityPrice ep) {
		
		
			//listMonthlyStatistics.clear(); /* This line prevents the saved year from not matching the argument */
		
			// UPGRADE: check that visibility is not violated 
			//System.out.println("Visibility Statistics: Minutes Visible, % Visible:" + 
			//		minutesVisibleMonthly + "," +
			//		(minutesVisibleMonthly/ (double) minutesInSimulationSincePreviousReset)*100);
			//if (minutesVisibleMonthly > iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
				
			//	System.out.println("Warning: mituesVisible surpassed iUsedAllItsAllocatedVisibilityMinutesPerMonth");
				
			//}
				
			minutesFullyStowedMonthly = 0;
			
			//printMinutesVisibleMonthly();
			
			minutesVisibleMonthly = 0;

			minutesFullyDeployedMonthly = 0;
			
			//System.out.println("Verification: Days since last reset: " + minutesInSimulationSincePreviousReset/60/24);
			
			minutesInSimulationSincePreviousReset = 0;
			
			dTotalEnergyHarvestedKilowattMinuteMonthPrevious =
					dTotalEnergyHarvestedKilowattMinuteThisMonth;
			dTotalEnergyHarvestedKilowattMinuteThisMonth = 0;

			iVisibilityEventsMonthly = 0;
			
			ep.resetCountersMonthly();
						
	}
	

	
	public void resetAll(ElectricityPrice ep) {
		
		// Time Statistics
		//resetMonthlyStatistics(iUsedAllItsAllocatedVisibilityMinutesPerMonth);
		resetMonthlyStatistics(ep);

		// Power Statistics
		dTotalEnergyAvailableKilowattMinute = 0;
		dTotalEnergyHarvestedKilowattMinute = 0;

		dTotalEnergyAvailableDuringQuietHoursKilowattMinute = 0;
		
		dTotalEnergyHarvestedPrice = 0;		
		
		ep.resetAll();
					
}


	/* Fraction of period that
	 * that the harvester has been visible
	 */
	public float getFractionVisbileMonthly(long maximumMinutesAllowedToBeVisible) {
		
		return minutesVisibleMonthly / (float) maximumMinutesAllowedToBeVisible;

	}
	
	public long getRemainingAllocationOfVisiblityMinutes(long maximumMinutesAllowedToBeVisible) {
		
		return(maximumMinutesAllowedToBeVisible - minutesVisibleMonthly); 
		
	}

	
	/* Fraction of period that
	 * that the harvester has been visible
	 */
	public float getFractionVisbilePlusTimeToRetractMonthly(long maximumMinutesAllowedToBeVisible) {
		
		return (minutesVisibleMonthly + TIME_TO_RETRACT_MINUTES ) / (float) maximumMinutesAllowedToBeVisible;

	}
	
	
	public float getMembershipValueForPlentyOfAllocatedVisibilityMinutesRemaining(long maximumMinutesAllowedToBeVisible) {
		
		float membershipValue = 1 - getFractionVisbilePlusTimeToRetractMonthly(maximumMinutesAllowedToBeVisible);
		
		float ADJUSTMENT = (float) -0.1; // account for adjective "Plenty".   The plenty of time left overcomes the negative adjustment 
		membershipValue += ADJUSTMENT;
		
		if (membershipValue > 1) {
			
			membershipValue = 1;
			
		} else if (membershipValue < 0) {
			
			membershipValue = 0;
			
		}
		
		return(membershipValue);
		
	}
	
	public float getMembershipValueForPlentyOfAllocatedVisibilityMinutesRemainingShiftX(long maximumMinutesAllowedToBeVisible) {

		
		float ADJUSTMENT = (float) 0.9; // account for adjective "Plenty".   The plenty of time left overcomes the fractional adjustment 
		
		float membershipValue = 1 - getFractionVisbilePlusTimeToRetractMonthly((int) ADJUSTMENT  * maximumMinutesAllowedToBeVisible);
		
		if (membershipValue > 1) {
			
			membershipValue = 1;
			
		} else if (membershipValue < 0) {
			
			membershipValue = 0;
			
		}
		
		return(membershipValue);
		
	}
	

	public void printMinutesVisibleMonthly() {
		
		System.out.println("minutesVisibleMonthly: " + minutesVisibleMonthly);
		
	}
	
	public double getDeploymentEnergyUsedPerMinuteKilowattMinute(int time_to_deploy_minutes, int windspeed_knots) {		

		return(
					getDeploymentEnergyUsedPerMinuteKwh(
							time_to_deploy_minutes, windspeed_knots)/60.0
					);

	}	
	
	public int getDeploymentEnergyUsedPerMinuteKwh(int time_to_deploy_minutes, int windspeed_knots) {

		// Assuming all energy to lift harvester can be provided by airfoils
		// when wind speed is 20 knots or higher
		final int DEPLOYMENT_ENERGY_CONSUMPTION_THRESHOLD_KWH = (int) Math.pow(20, 3);
		final double DEPLOYMENT_ENERGY_SCALING_FACTOR = .004;
		//final double DEPLOYMENT_ENERGY_SCALING_FACTOR = 0;

		
		double energy_required_to_lift_kwh_per_event = (DEPLOYMENT_ENERGY_CONSUMPTION_THRESHOLD_KWH
				- Math.pow(windspeed_knots, 3)) * DEPLOYMENT_ENERGY_SCALING_FACTOR;

		if (energy_required_to_lift_kwh_per_event < 0)
			energy_required_to_lift_kwh_per_event = 0;

		return ((int) energy_required_to_lift_kwh_per_event / time_to_deploy_minutes);

	}
	
    /*
	public long getMinutesVisibleMonthly(int arrayIndex, 
			int yearCheck, 
			int monthValueCheck) {
		
Type	MonthlyStatistics ms = listMonthlyStatistics.get(arrayIndex);
		
		if (ms.year != yearCheck) {
			
			System.out.println("Warning: saved year " +  ms.year +
											 " does not match argument " + yearCheck);
			
		}

		if (ms.month.getValue() != monthValueCheck) {
			
			System.out.println("Warning: saved month " +  ms.month.getValue() +
											 " does not match argument " +monthValueCheck );
						
		}
		
		return(ms.minutesVisible);
		
	}
    */
    
    public enum MonthlyStatisticType {

	MINUTES_VISIBLE, MINUTES_HARVESTER_BETWEEN_STATES

    }

	public long getMinutesMonthlyStatistic(int arrayIndex, 
					       int yearCheck, 
					       int monthValueCheck,
					       MonthlyStatisticType monthlyStatistic
					       ) {
		
		MonthlyStatistics ms = listMonthlyStatistics.get(arrayIndex);
		
		if (ms.year != yearCheck) {
			
			System.out.println("Warning: saved year " +  ms.year +
											 " does not match argument " + yearCheck);
			
		}

		if (ms.month.getValue() != monthValueCheck) {
			
			System.out.println("Warning: saved month " +  ms.month.getValue() +
											 " does not match argument " +monthValueCheck );
						
		}
		
		long valueToReturn;

		switch(monthlyStatistic)
		    {
		    case MINUTES_VISIBLE:
			valueToReturn = ms.minutesVisible;
			break;
		    case MINUTES_HARVESTER_BETWEEN_STATES:
			valueToReturn = ms.minutesVisible-ms.minutesFullyDeployed;
			break;
		    default:
			valueToReturn = -1;
			break;
		    }


		return(valueToReturn);
		
	}
	
	public class MonthlyStatistics {

		public MonthlyStatistics(LocalDateTime date,
					 long minutesVisibleMonthly,
					 int minutesFullyDeployedMonthly) {

			// To get year and month, subtract a day since incoming day is
			//  the first sample of new month
			LocalDateTime prevDay = date.minusDays(1);
			this.year = prevDay.getYear();
			this.month = prevDay.getMonth();
			this.minutesVisible = minutesVisibleMonthly;
			this.minutesFullyDeployed = minutesFullyDeployedMonthly;

			
		}
	    int year;
	    java.time.Month month;
	    long minutesVisible;
	    int minutesFullyDeployed;
    
	    
		
	}
}
