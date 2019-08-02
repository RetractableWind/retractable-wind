package edu.pitt.cs.people.guy.wind.benchmarks;

import java.time.LocalDateTime;
import java.util.Arrays;


public class Aging {

	HarvesterModel harvester; 
	Workload ws;
	String station;
	NoiseAllowed noiseAllowed, noiseAllowedFutureRetractionTime;
	ElectricityPrice ep;
	boolean bTransitionsLimited;
	Windy windy;

	RunningAverage ra;
	RunningAverage raf; // future
	int running_average_window_size;

	double energyHarvestedKWH = 0;

	// The following values are set during the training phase
	final int NUMBER_OF_MONTHS_PLUS_ONE = 13; // index 0 is not used b/c .getMonthValue is 1 to 12
	
	final int Y_INTERCEPT_START;
    //final int Y_INTERCEPT_STEP = 10;
	final int Y_INTERCEPT_STEP = 1;
	final int Y_INTERCEPT_END;

	int y_intercept;
	
	int running_average_minutes_best[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];
	int running_average_minutes_best_market[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];
	
	int y_intercept_best[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];
	double energyHarvestedKWHMax[] = new double[NUMBER_OF_MONTHS_PLUS_ONE];
	
	double productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[] =
			new double[NUMBER_OF_MONTHS_PLUS_ONE];
	
	int y_intercept_best_market[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];
	

	private void initializeArrays() {
	

		Arrays.fill(y_intercept_best, Y_INTERCEPT_START);
		Arrays.fill(y_intercept_best_market, Y_INTERCEPT_START);

		Arrays.fill(energyHarvestedKWHMax, Double.MIN_VALUE);
		Arrays.fill(productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax,
				Double.MAX_VALUE);		
				
	}

	public Aging(String localStation,
			HarvesterModel localHarvester,
			Workload localWorkload,
		     boolean bTransitionsLimited_local,
		     float lambda) {

		System.out.println("Class name:" + getClass().getName());
		
		harvester = localHarvester;

		ws = localWorkload;

		station = localStation;
		
		bTransitionsLimited = bTransitionsLimited_local;
		
		noiseAllowed = new NoiseAllowed(station);
		noiseAllowedFutureRetractionTime = new NoiseAllowed(station);

		ep = new ElectricityPrice(ws.HOURLY_ENERGY_PRICE_FILENAME);
		
		windy = new Windy(station, lambda);
		
		Y_INTERCEPT_START = windy.getLowestWindspeedThatIsWindyKnots();
		System.out.println("LowestWindspeedThatIsWindyKnots for station " +
				station + " is " + Y_INTERCEPT_START + " knots.");
		//Y_INTERCEPT_END = Y_INTERCEPT_START + (3*Y_INTERCEPT_STEP);
		Y_INTERCEPT_END = Y_INTERCEPT_START + (30*Y_INTERCEPT_STEP);
		y_intercept = Y_INTERCEPT_START;
				
		initializeArrays();
		
	}




	private int getDeploymentThresholdKnots(LocalDateTime dateTime) {

		final int MINUTES_IN_MONTH_MAXIMUM = 31 * 24 * 60;
		
		// Y_INTERCEPT_START is the lowest windspeed that is considered
		// to be windy.
		final int DEPLOYMENT_THRESHOLD_MINIMUM_KNOTS = Y_INTERCEPT_START;

		final int RISE = DEPLOYMENT_THRESHOLD_MINIMUM_KNOTS - y_intercept;
		final double RUN = ((double) MINUTES_IN_MONTH_MAXIMUM - 0);
		final double SLOPE = RISE/RUN;
		
		int deploymentThreshold = (int) (SLOPE *
				DateStatistics.getMinutesInMonthRemaining(dateTime) +
				y_intercept);

		return(deploymentThreshold);

	}


	private double processOneIterationOfTrainingSamples(int[] running_avg_size_minutes, boolean bUseWeatherPrediction) {

		ra  = new RunningAverage(running_avg_size_minutes) ;
		raf = new RunningAverage(running_avg_size_minutes);
		running_average_window_size = running_avg_size_minutes[0];	 // grab first element since all are same at this point
		Workload.WindspeedSample  sample;

		ws.reopenReaderTrain();
		
		reset();

		int monthValuePrevious = 1;
		while((sample = ws.getNextWindspeedSampleTraining()) != null ) {

			// Determine what control signal to output.
			// Use visibility-time-remaining to control deployment threshold
						processOneSample(sample, bUseWeatherPrediction);
						
			// Upon month ending, update maximum for the previous month						
						
						if (monthValuePrevious != sample.date.getMonthValue()) {
							
							if (energyHarvestedKWHMax[monthValuePrevious] <
									harvester.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious()) {
								
								energyHarvestedKWHMax[monthValuePrevious] =
										harvester.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious();
								y_intercept_best[monthValuePrevious] = y_intercept;
								running_average_minutes_best[monthValuePrevious] =
										running_avg_size_minutes[monthValuePrevious];
//								System.out.println("Max. update for month: " +
//								monthValuePrevious +
//								".  The y-int is " + y_intercept);
								
							}
							
							double productOfEnergyHarvestedKilowattMinuteAndMarketProportion =
									ep.getMarketBalancePercentageThisMonthPrevious() *
									harvester.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious();

							if (productOfEnergyHarvestedKilowattMinuteAndMarketProportion  >
							productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[monthValuePrevious]) {

								y_intercept_best_market[monthValuePrevious] = y_intercept;
								running_average_minutes_best_market[monthValuePrevious] =
										running_avg_size_minutes[monthValuePrevious];
								productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[monthValuePrevious] =
										productOfEnergyHarvestedKilowattMinuteAndMarketProportion;
							
							}
							
							
							
							monthValuePrevious = sample.date.getMonthValue();
						
						}

		}

		System.out.println("Energy harvested: " + harvester.getEnergyHarvestedKilowattHours());
		
		int[] local_y_intercept_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(local_y_intercept_array, y_intercept);
		//printResults(running_avg_size_minutes, local_y_intercept_array);

		
		return(harvester.getEnergyHarvestedKilowattHours());

	}

	public void searchForBestYInterceptInnerLoop(int running_average_minutes, boolean bUseWeatherPrediction) {

		// Loop that searches for best Y_INTERCEPT in the context of passed running_average_minutes

		for(y_intercept = Y_INTERCEPT_START;
				y_intercept <= Y_INTERCEPT_END;	 
				y_intercept+= Y_INTERCEPT_STEP) {
			
			System.out.println("The y-int is " + y_intercept);
			int[] running_average_minutes_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
			Arrays.fill(running_average_minutes_array, running_average_minutes);
			double energyHarvestedKWH =processOneIterationOfTrainingSamples(running_average_minutes_array, bUseWeatherPrediction);

			ep.printStatisticsOfGridMatching();
			//ep.closeFiles();

			System.out.println("harvester.getEnergyHarvestedAVAILABLEKilowattHours( ): " + 
					harvester.getEnergyHarvestedAVAILABLEKilowattHours());
			
			System.out.println("harvester.getEnergyHarvestedKilowattHours(): " + 
					harvester.getEnergyHarvestedKilowattHours());

			
			//System.exit(0);

		}	
		
	}

	
	public void searchForBestRunningAverageMinutesAndYInterceptPair(boolean bUseWeatherPrediction) {

		// Loop that searches for best Y_INTERCEPT
/*		final int RUNNING_AVERAGE_MINUTES_START = 1; 	
    	final int  RUNNING_AVERAGE_MINUTES_END = 121;
		final int RUNNING_AVERAGE_MINUTES_STEP = 5;
*/
		final int RUNNING_AVERAGE_MINUTES_START = 1; 	
		/*    	final int  RUNNING_AVERAGE_MINUTES_END = 121;
		final int RUNNING_AVERAGE_MINUTES_STEP = 30;
		*/
    	final int  RUNNING_AVERAGE_MINUTES_END = 361;
		final int RUNNING_AVERAGE_MINUTES_STEP = 1;

		Arrays.fill(energyHarvestedKWHMax, Double.MIN_VALUE);
		Arrays.fill(productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax, Double.MIN_VALUE);
		
		Arrays.fill(y_intercept_best, Y_INTERCEPT_START);		
		Arrays.fill(y_intercept_best_market, Y_INTERCEPT_START);
		Arrays.fill(running_average_minutes_best, RUNNING_AVERAGE_MINUTES_START);
		Arrays.fill(running_average_minutes_best_market, RUNNING_AVERAGE_MINUTES_START);

		int running_average_minutes;
		for(running_average_minutes = RUNNING_AVERAGE_MINUTES_START;
				running_average_minutes <= RUNNING_AVERAGE_MINUTES_END;
				running_average_minutes+=RUNNING_AVERAGE_MINUTES_STEP) {
				
			System.out.println("The running average is " + running_average_minutes);
			
			searchForBestYInterceptInnerLoop(running_average_minutes, bUseWeatherPrediction);

		}	

		
		//printResults(running_average_minutes_best, y_intercept_best);

		
	}
	
	
	public void train(boolean bUseWeatherPrediction) {

		searchForBestRunningAverageMinutesAndYInterceptPair(bUseWeatherPrediction);
		
	}

	private void processOneSample(Workload.WindspeedSample  sample, boolean bUseWeatherPrediction) {
		
		if (bTransitionsLimited) {
			
			processOneSampleTransitionsLimited(sample, bUseWeatherPrediction);
			
		} else {
			
			processOneSampleTransitionsUnlimited(sample, bUseWeatherPrediction);
			
		}
		
	}
	
	
	private void processOneSampleTransitionsLimited(Workload.WindspeedSample  sample,
			boolean bUseWeatherPrediction) {
		
		final int RETRACTION_THRESHOLD_DIFFERENCE = 1;
		
		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		int deploymentThresholdKnots = getDeploymentThresholdKnots(sample.date);

		double windspeed_knots_average = ra.updateRunningAverage(sample.windspeed_knots, running_average_window_size);
		
		final double MUCH_WINDIER = 1.25; 
		double windspeed_knots_average_future =
				ra.updateRunningAverage(sample.windspeed_knots_predicted_one_day, running_average_window_size); 
		
		if (
				(harvester.getMinutesVisibleMonthly() < 1) &&
				(
					(DateStatistics.getMinutesInMonthRemaining(sample.date) < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
					||
				// harvester has not yet been visible this month
					(
						(windspeed_knots_average > deploymentThresholdKnots) &&
							(!(bUseWeatherPrediction &&
									(windspeed_knots_average_future > (windspeed_knots_average*MUCH_WINDIER)) // much windier tomorrow
									)
							)
					)
				)
			)	
		{

			harvester.setMode();

		} 

		// check amount of time used per month
		final float FRACTION_VISIBLE_TIME_THRESHOLD = (float) 0.99;
		if (harvester. getFractionVisbilePlusTimeToRetractMonthly(ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) >
		FRACTION_VISIBLE_TIME_THRESHOLD){

			harvester.resetMode();

		}
		
		
		// if within retraction time of end of month, then retract
		if (DateStatistics.getMinutesInMonthRemaining(sample.date) <=
								harvester.TIME_TO_RETRACT_MINUTES
				) {
			
			harvester.resetMode();
			
		}

		harvester.processMode(sample, ep, false);
		
	}
	
	
	private void processOneSampleTransitionsUnlimited(Workload.WindspeedSample  sample,
			boolean bUseWeatherPrediction) {
		
		final int RETRACTION_THRESHOLD_DIFFERENCE = 3;
		
		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		int deploymentThresholdKnots = getDeploymentThresholdKnots(sample.date);
		int retractionThresholdKnots = deploymentThresholdKnots - RETRACTION_THRESHOLD_DIFFERENCE; 

		double windspeed_knots_average = ra.updateRunningAverage(sample.windspeed_knots, running_average_window_size);
		
		if (windspeed_knots_average > deploymentThresholdKnots) {

			harvester.setMode();

		} else if (windspeed_knots_average < retractionThresholdKnots) {

			harvester.resetMode();

		}

		// check amount of time used per month
		final float FRACTION_VISIBLE_TIME_THRESHOLD = (float) 0.99;
		double fractionVisbilePlusTimeToRetractMonthly =
				harvester. getFractionVisbilePlusTimeToRetractMonthly(ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
				
		if (fractionVisbilePlusTimeToRetractMonthly > FRACTION_VISIBLE_TIME_THRESHOLD){

			harvester.resetMode();

		}
		
		// If harvester has somewhat nearly exhausted is allocated visible time for the month and
		// tomorrow will be much windier than today,
		// then retract to save visibility time
		if (bUseWeatherPrediction) {
			
			final double MUCH_WINDIER = 1.25; 
			final double FRACTION_VISIBLE_TIME_THRESHOLD_SOMEWHAT_EXHAUSTED = 0.64; 
			double windspeed_knots_average_future =
					ra.updateRunningAverage(sample.windspeed_knots_predicted_one_day, running_average_window_size); 
			
			if (
					(fractionVisbilePlusTimeToRetractMonthly > 
				FRACTION_VISIBLE_TIME_THRESHOLD_SOMEWHAT_EXHAUSTED) &&
			(windspeed_knots_average_future > (windspeed_knots_average*MUCH_WINDIER))
			){
				
				//System.out.println("Retracting because future windspeeds are much windier...");
				harvester.resetMode();
				
			}
			
		}
		
		// if within retraction time of quiet hours or during quiet hours, then retract or remain retracted
		if (ws.bDuringNightlyVisibilityBan &
				(
					!noiseAllowedFutureRetractionTime.bIsNoiseAllowed(sample.date.plusMinutes(harvester.TIME_TO_RETRACT_MINUTES))
					| // do not short circuit
					!(noiseAllowed.bIsNoiseAllowed(sample.date))
					
					)
				){
			
			harvester.resetMode();
			
		}
		

		harvester.processMode(sample, ep, true);
		
		
	}

    private void reset() {
    	
		harvester.resetAll(ep);
		noiseAllowed.reset();
		noiseAllowedFutureRetractionTime.reset();
		
    }
	
	private double processOneIterationOfTestingSamples(int[] running_avg_size_minutes,
			int[] y_intercept_best,
			boolean bUseWeatherPrediction) {

		ra  = new RunningAverage(running_avg_size_minutes);
		raf = new RunningAverage(running_avg_size_minutes); 
		
		Workload.WindspeedSample  sample;

		ws.openTestingFile();
		
		reset();		

		while((sample = ws.getNextWindspeedSampleTesting()) != null ) {
			
			y_intercept = y_intercept_best[sample.date.getMonthValue()]; //get best y-intercept for month
			running_average_window_size = running_avg_size_minutes[sample.date.getMonthValue()];// bet best running-average-window-size for month
			processOneSample(sample, bUseWeatherPrediction);
			
		}
		harvester.addToMonthlyStatisticsListForFINALMonth();
		printResults(running_avg_size_minutes, y_intercept_best);
		
		return(harvester.getEnergyHarvestedKilowattHours());

	}

	
	
	public void printResults(int[] running_avg_size_minutes, int[] local_y_intercept_array) {
		
		Results.printResults(station, 
				harvester, 
				ep, 
				running_avg_size_minutes,
				local_y_intercept_array);		
		
	}
		
	
	public void testing(boolean bUseWeatherPrediction) {
		
	
		processOneIterationOfTestingSamples(running_average_minutes_best, y_intercept_best, bUseWeatherPrediction);
		
		processOneIterationOfTestingSamples(running_average_minutes_best_market,
				y_intercept_best_market, bUseWeatherPrediction);
		
		
	}
	
	
}
