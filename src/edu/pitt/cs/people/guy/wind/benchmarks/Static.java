package edu.pitt.cs.people.guy.wind.benchmarks;


import java.time.LocalDateTime;
import java.util.Arrays;

import edu.pitt.cs.people.guy.wind.benchmarks.Workload.WindspeedSample;


public class Static {

	HarvesterModel harvester; 
	Workload ws;
	String station;
	NoiseAllowed noiseAllowed, noiseAllowedFutureRetractionTime;
	ElectricityPrice ep;
	boolean bTransitionsLimited;
	Windy windy;
	final int NUMBER_OF_MONTHS_PLUS_ONE = 13;
	RunningAverage ra;
	RunningAverage raf; // future
	int running_average_window_size;

	double energyHarvestedKWH = 0;

	// The following values are set during the training phase	
	final int Y_INTERCEPT_START;
	final int Y_INTERCEPT_STEP = 10; //Design space: v1.0
	//final int Y_INTERCEPT_STEP = 1; //Design space: v2.0
	final int Y_INTERCEPT_END;

	int y_intercept;
	
	int running_average_minutes_best;
	int running_average_minutes_best_market;
	
	int y_intercept_best;
	int y_intercept_best_market;
	
	double energyHarvestedKWHMax;
	
	double productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax;
	

	

	private void initializeSuperlatives() {
	

		y_intercept_best = Y_INTERCEPT_START;
		y_intercept_best_market = Y_INTERCEPT_START;

		energyHarvestedKWHMax = Double.MIN_VALUE;
		productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax =
				Double.MAX_VALUE;		 
				
	}

	public Static(String localStation,
			HarvesterModel localHarvester,
			Workload localWorkload,
		      boolean bTransitionsLimited_local, float lambda) {

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
		y_intercept = Y_INTERCEPT_START;
		Y_INTERCEPT_END = Y_INTERCEPT_START + (3*Y_INTERCEPT_STEP);
		//Y_INTERCEPT_END = Y_INTERCEPT_START + (7*Y_INTERCEPT_STEP);
		//Y_INTERCEPT_END = Y_INTERCEPT_START; //comment out after test
		initializeSuperlatives();
		
	}


	// For constant deployment threshold
	private int getDeploymentThresholdKnots() {
		
		return(y_intercept);

	}


	private double processOneIterationOfTrainingSamples(int[] running_avg_size_minutes, boolean bUseWeatherPrediction) {

		ra  = new RunningAverage(running_avg_size_minutes) ;
		raf = new RunningAverage(running_avg_size_minutes);
		running_average_window_size = running_avg_size_minutes[0];	 // grab first element since all are same at this point
		Workload.WindspeedSample  sample;

		ws.reopenReaderTrain();
		
		reset();


		while((sample = ws.getNextWindspeedSampleTraining()) != null ) {

			// Determine what control signal to output.
			// Use visibility-time-remaining to control deployment threshold
						processOneSample(sample, bUseWeatherPrediction);
						

		} //while

		
		// Upon training period ending, update superlatives						
		
		if (energyHarvestedKWHMax <
				harvester.getEnergyHarvestedKilowattHours()) {
				
			energyHarvestedKWHMax =
					harvester.getEnergyHarvestedKilowattHours();
			y_intercept_best = y_intercept;
			running_average_minutes_best =
					running_average_window_size;
			
		}
		
		double productOfEnergyHarvestedKilowattMinuteAndMarketProportion =
				ep.getMarketBalancePercentageThisMonthPrevious() *
				harvester.getEnergyHarvestedKilowattHours()/60;

		if (productOfEnergyHarvestedKilowattMinuteAndMarketProportion  >
		productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax) {

			y_intercept_best_market = y_intercept;
			running_average_minutes_best_market =
					running_average_window_size;
			productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax =
					productOfEnergyHarvestedKilowattMinuteAndMarketProportion;
		
		}
		
		System.out.println("Energy harvested: " + harvester.getEnergyHarvestedKilowattHours());
		
		
		return(harvester.getEnergyHarvestedKilowattHours());

	}

	public void searchForBestYInterceptInnerLoop(int running_average_minutes, boolean bUseWeatherPrediction) {

		// Loop that searches for best Y_INTERCEPT in the context of passed running_average_minutes

		for(y_intercept = Y_INTERCEPT_START;
				y_intercept <= Y_INTERCEPT_END;	 y_intercept+= Y_INTERCEPT_STEP) {

			System.out.println("The y-int is " + y_intercept);

			int[] running_average_minutes_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
			Arrays.fill(running_average_minutes_array, running_average_minutes);
			processOneIterationOfTrainingSamples(running_average_minutes_array, bUseWeatherPrediction);

			ep.printStatisticsOfGridMatching();
			//ep.closeFiles();

			System.out.println("harvester.getEnergyHarvestedAVAILABLEKilowattHours( ): " + 
					harvester.getEnergyHarvestedAVAILABLEKilowattHours());
			
			System.out.println("harvester.getEnergyHarvestedKilowattHours(): " + 
					harvester.getEnergyHarvestedKilowattHours());

		}	
		
	}

	
	public void searchForBestRunningAverageMinutesAndConstantY(boolean bUseWeatherPrediction) {

		// Loop that searches for best Y_INTERCEPT
		final int RUNNING_AVERAGE_MINUTES_START = 1; 	
    	final int  RUNNING_AVERAGE_MINUTES_END = 121;
		final int RUNNING_AVERAGE_MINUTES_STEP = 30;
		
//		final int RUNNING_AVERAGE_MINUTES_START = 176; 	
//    	//final int  RUNNING_AVERAGE_MINUTES_END = 121;
//		final int  RUNNING_AVERAGE_MINUTES_END = 196;
//		final int RUNNING_AVERAGE_MINUTES_STEP = 1;

	    //final int RUNNING_AVERAGE_MINUTES_START = 1; 	
	    //final int  RUNNING_AVERAGE_MINUTES_END = 361;
	    //final int RUNNING_AVERAGE_MINUTES_STEP = 1;
		
		
		energyHarvestedKWHMax = Double.MIN_VALUE;
		productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax = Double.MIN_VALUE;
		
		y_intercept_best = Y_INTERCEPT_START;		
		y_intercept_best_market = Y_INTERCEPT_START;
		running_average_minutes_best = RUNNING_AVERAGE_MINUTES_START;
		running_average_minutes_best_market = RUNNING_AVERAGE_MINUTES_START;

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

		searchForBestRunningAverageMinutesAndConstantY(bUseWeatherPrediction);
		
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
		
		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		int deploymentThresholdKnots = getDeploymentThresholdKnots();

		double windspeed_knots_average = ra.updateRunningAverage(sample.windspeed_knots, running_average_window_size);
		
		
		final double MUCH_WINDIER = 1.25; 
		double windspeed_knots_average_future =
				raf.updateRunningAverage(sample.windspeed_knots_predicted_one_day, running_average_window_size); 
		
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
	
	//boolean bPrevNoiseState;
	
	private void processOneSampleTransitionsUnlimited(Workload.WindspeedSample  sample,
			boolean bUseWeatherPrediction) {
		
		
		// if within retraction time of quiet hours or during quiet hours, then retract or remain retracted
//    //For debugging:
//		boolean bCurrentNoiseState =  noiseAllowed.bIsNoiseAllowed(sample.date);
//		if (bPrevNoiseState != bCurrentNoiseState) {
//					
//			System.out.println("Noise-allowed state change on " + sample.date + " to " + bCurrentNoiseState);
//			bPrevNoiseState = bCurrentNoiseState;
//			
//		}
		
		
		final int RETRACTION_THRESHOLD_DIFFERENCE = 1;
		
		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		int deploymentThresholdKnots = getDeploymentThresholdKnots();
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
					raf.updateRunningAverage(sample.windspeed_knots_predicted_one_day, running_average_window_size); 
			
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
		
		boolean bRetractWhenInCutOutRange = true;
		harvester.processMode(sample, ep, bRetractWhenInCutOutRange);
		
		
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
		Workload.WindspeedSample  prevSample = ws.new WindspeedSample(); // get blank sample
	
		ws.openTestingFile();
		
		reset();		

		while((sample = ws.getNextWindspeedSampleTesting()) != null ) {
			
			y_intercept = y_intercept_best[sample.date.getMonthValue()]; //get best y-intercept for month
			running_average_window_size = running_avg_size_minutes[sample.date.getMonthValue()];// bet best running-average-window-size for month
			processOneSample(sample, bUseWeatherPrediction);
			prevSample = sample;
		}
		//harvester.addToMonthlyStatisticsList(prevSample.date, harvester.getMinutesVisibleMonthly());
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
		
		/*
		
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
		
		
		int arrayIndex = 1; // ignore first element in array
		
		for (int year = 2009; year <= 2014; year++) {

			System.out.print("monthly visible minutes:" + station + ",&," + year + ",&,");		
			
			for (int month =1; month <=12; month++) {
				
				System.out.print(harvester.getMinutesVisibleMonthly(arrayIndex, year, month) + ",&,");		
				arrayIndex++;
				
			}
			
			System.out.println();
			
		}
			
//		System.out.println("FractionOfEnergyAvailableDuringQuietHoursOverEnergyAvailable: " +
//				station + ",&," + harvester.getFractionOfEnergyAvailableDuringQuietHoursOverEnergyAvailable());

		*/
		
	}
	

	public void testing(boolean bUseWeatherPrediction) {
		
		
		int[] y_intercept_best_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(y_intercept_best_array, y_intercept_best);

		
		int[] running_average_minutes_best_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(running_average_minutes_best_array, running_average_minutes_best);
		
		processOneIterationOfTestingSamples(running_average_minutes_best_array,
				y_intercept_best_array, bUseWeatherPrediction);
		
		
		int[] running_average_minutes_best_market_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(running_average_minutes_best_market_array, running_average_minutes_best_market);
		
		int[] y_intercept_best_market_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(y_intercept_best_market_array, y_intercept_best_market);

		processOneIterationOfTestingSamples(running_average_minutes_best_market_array,
				y_intercept_best_market_array, bUseWeatherPrediction);
		

		
		
	}
	
	
	public void testing(boolean bUseWeatherPrediction,
			int y_intercept_best_LOCAL,
			int running_average_minutes_best_LOCAL,
			int running_average_minutes_best_market_LOCAL,
			int y_intercept_best_market_LOCAL) {
		
		
		int[] y_intercept_best_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(y_intercept_best_array, y_intercept_best_LOCAL);

		
		int[] running_average_minutes_best_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(running_average_minutes_best_array, running_average_minutes_best_LOCAL);
		
		processOneIterationOfTestingSamples(running_average_minutes_best_array,
				y_intercept_best_array, bUseWeatherPrediction);

		/* ****** */
		
		int[] running_average_minutes_best_market_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(running_average_minutes_best_market_array,
				running_average_minutes_best_market_LOCAL);
		
		int[] y_intercept_best_market_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(y_intercept_best_market_array, y_intercept_best_market_LOCAL);

		processOneIterationOfTestingSamples(running_average_minutes_best_market_array,
				y_intercept_best_market_array, bUseWeatherPrediction);

		
		
	}
	
	
}
