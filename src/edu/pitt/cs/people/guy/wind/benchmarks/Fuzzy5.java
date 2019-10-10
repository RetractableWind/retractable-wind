package edu.pitt.cs.people.guy.wind.benchmarks;

import java.time.LocalDateTime;
import java.util.Arrays;

public class Fuzzy5 {

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
	
	final int RETRACTION_THRESHOLD_DIFFERENCE = 1;

	// The following values are set during the training phase
	final int NUMBER_OF_MONTHS_PLUS_ONE = 13; // index 0 is not used b/c .getMonthValue is 1 to 12

	// final double DEPLOYMENT_THRESHOLD_MV_START = 0.5; // we chose the lamda cut to 0.5
        //  final double DEPLOYMENT_THRESHOLD_MV_STEP = 0.1;
	//  final double DEPLOYMENT_THRESHOLD_MV_END = 0.5;
	// //final double DEPLOYMENT_THRESHOLD_MV_END = 0.1;



    	final double DEPLOYMENT_THRESHOLD_MV_START = 0.1; // we chose the lamda cut to 0.5
    	final double DEPLOYMENT_THRESHOLD_MV_STEP = 0.1;
    final double DEPLOYMENT_THRESHOLD_MV_END = 0.9;
   	//final double DEPLOYMENT_THRESHOLD_MV_END = 0.1;


	double deployment_threshold_mv;

	int running_average_minutes_best[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];
	int running_average_minutes_best_market[] = new int[NUMBER_OF_MONTHS_PLUS_ONE];

	double deployment_threshold_mv_best[] = new double[NUMBER_OF_MONTHS_PLUS_ONE];
	double energyHarvestedKWHMax[] = new double[NUMBER_OF_MONTHS_PLUS_ONE];

	double productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[] = new double[NUMBER_OF_MONTHS_PLUS_ONE];

	double deployment_threshold_mv_best_market[] = new double[NUMBER_OF_MONTHS_PLUS_ONE];
	
	long lCrispConditionalEvaluated = 0;
	long lCrispRetractionThresholdReached = 0;
	long lFuzzyStricterThanCrisp  = 0;
	long lCrispFoundNoReasonToRetract = 0;
	boolean bEveryMoreLenientCaseRestricted = true;
	boolean bEveryMoreLenientCaseReachesFuzzyRetractionThreshold = true;

	
	private void initializeArrays() {

		Arrays.fill(deployment_threshold_mv_best, DEPLOYMENT_THRESHOLD_MV_START);
		Arrays.fill(deployment_threshold_mv_best_market, DEPLOYMENT_THRESHOLD_MV_START);

		Arrays.fill(energyHarvestedKWHMax, Double.MIN_VALUE);
		Arrays.fill(productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax, Double.MAX_VALUE);

	}

	public Fuzzy5(String localStation_local, HarvesterModel localHarvester_local, Workload localWorkload_local,
		      boolean bTransitionsLimited_local, float lambda) {

		
		System.out.println("Class name:" + getClass().getName());
		
		station = localStation_local;
		harvester = localHarvester_local;
		ws = localWorkload_local;
		bTransitionsLimited = bTransitionsLimited_local;

		noiseAllowed = new NoiseAllowed(station);
		noiseAllowedFutureRetractionTime = new NoiseAllowed(station);

		ep = new ElectricityPrice(ws.HOURLY_ENERGY_PRICE_FILENAME);

		windy = new Windy(station, lambda);

		// 		DEPLOYMENT_THRESHOLD_MV_START = windy.getLowestWindspeedThatIsWindyKnots();
		System.out.println("LowestWindspeedThatIsWindyKnots for station " + station + " is "
				   + windy.getLowestWindspeedThatIsWindyKnots() + " knots.");
		//DEPLOYMENT_THRESHOLD_MV_END = DEPLOYMENT_THRESHOLD_MV_START +
		//(7*DEPLOYMENT_THRESHOLD_STEP);
		//deployment_threshold_mv = DEPLOYMENT_THRESHOLD_MV_START;

		initializeArrays();

	}

	private double processOneIterationOfTrainingSamples(int[] running_avg_size_minutes, boolean bUseWeatherPrediction) {

		ra = new RunningAverage(running_avg_size_minutes);
		raf = new RunningAverage(running_avg_size_minutes);
		running_average_window_size = running_avg_size_minutes[0]; // grab first element since all are same at this
																	// point
		Workload.WindspeedSample sample;

		ws.reopenReaderTrain();

		reset();

		int monthValuePrevious = 1;
		while ((sample = ws.getNextWindspeedSampleTraining()) != null) {

			// Determine what control signal to output.
			// Use visibility-time-remaining to control deployment threshold
			processOneSample(sample, bUseWeatherPrediction);

			// Upon month ending, update maximum for the previous month

			if (monthValuePrevious != sample.date.getMonthValue()) {

				if (energyHarvestedKWHMax[monthValuePrevious] < harvester
						.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious()) {

					energyHarvestedKWHMax[monthValuePrevious] = harvester
							.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious();
					deployment_threshold_mv_best[monthValuePrevious] = deployment_threshold_mv;
					running_average_minutes_best[monthValuePrevious] = running_avg_size_minutes[monthValuePrevious];
					// System.out.println("Max. update for month: " +
					// monthValuePrevious +
					// ". The y-int is " + deployment_threshold_mv);

				}

				double productOfEnergyHarvestedKilowattMinuteAndMarketProportion = ep
						.getMarketBalancePercentageThisMonthPrevious()
						* harvester.getTotalEnergyHarvestedKilowattMinuteThisMonthPrevious();

				if (productOfEnergyHarvestedKilowattMinuteAndMarketProportion > productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[monthValuePrevious]) {

					deployment_threshold_mv_best_market[monthValuePrevious] = deployment_threshold_mv;
					running_average_minutes_best_market[monthValuePrevious] = running_avg_size_minutes[monthValuePrevious];
					productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax[monthValuePrevious] = productOfEnergyHarvestedKilowattMinuteAndMarketProportion;

				}

				monthValuePrevious = sample.date.getMonthValue();

			}

		}

		System.out.println("Energy harvested: " + harvester.getEnergyHarvestedKilowattHours());

		double[] local_deployment_threshold_mv_array = new double[NUMBER_OF_MONTHS_PLUS_ONE];
		Arrays.fill(local_deployment_threshold_mv_array, deployment_threshold_mv);
		// printResults(running_avg_size_minutes, local_deployment_threshold_mv_array);

		return (harvester.getEnergyHarvestedKilowattHours());

	}

	public void searchForBestYInterceptInnerLoop(int running_average_minutes, boolean bUseWeatherPrediction) {

		// Loop that searches for best DEPLOYMENT_THRESHOLD in the context of passed
		// running_average_minutes

		for (deployment_threshold_mv = DEPLOYMENT_THRESHOLD_MV_START; deployment_threshold_mv <= DEPLOYMENT_THRESHOLD_MV_END; deployment_threshold_mv += DEPLOYMENT_THRESHOLD_MV_STEP) {

			System.out.println("The membership_value_deployment_threshold is " + deployment_threshold_mv);
			int[] running_average_minutes_array = new int[NUMBER_OF_MONTHS_PLUS_ONE];
			Arrays.fill(running_average_minutes_array, running_average_minutes);
			double energyHarvestedKWH = processOneIterationOfTrainingSamples(running_average_minutes_array,
					bUseWeatherPrediction);

			ep.printStatisticsOfGridMatching();
			// ep.closeFiles();

			System.out.println("harvester.getEnergyHarvestedAVAILABLEKilowattHours( ): "
					+ harvester.getEnergyHarvestedAVAILABLEKilowattHours());

			System.out.println(
					"harvester.getEnergyHarvestedKilowattHours(): " + harvester.getEnergyHarvestedKilowattHours());

			// System.exit(0);

		}

	}

	public void searchForBestRunningAverageMinutesAndYInterceptPair(boolean bUseWeatherPrediction) {

		// Loop that searches for best DEPLOYMENT_THRESHOLD
  		// final int RUNNING_AVERAGE_MINUTES_START = 1;
		// final int RUNNING_AVERAGE_MINUTES_END = 121;
		// final int RUNNING_AVERAGE_MINUTES_STEP = 30;
		
  		final int RUNNING_AVERAGE_MINUTES_START = 1;
		final int RUNNING_AVERAGE_MINUTES_END = 361;
		final int RUNNING_AVERAGE_MINUTES_STEP = 1;

		Arrays.fill(energyHarvestedKWHMax, Double.MIN_VALUE);
		Arrays.fill(productOfEnergyHarvestedKilowattMinuteAndMarketProportionMax, Double.MIN_VALUE);

		Arrays.fill(deployment_threshold_mv_best, DEPLOYMENT_THRESHOLD_MV_START);
		Arrays.fill(deployment_threshold_mv_best_market, DEPLOYMENT_THRESHOLD_MV_START);
		Arrays.fill(running_average_minutes_best, RUNNING_AVERAGE_MINUTES_START);
		Arrays.fill(running_average_minutes_best_market, RUNNING_AVERAGE_MINUTES_START);

		int running_average_minutes;
		for (running_average_minutes = RUNNING_AVERAGE_MINUTES_START; running_average_minutes <= RUNNING_AVERAGE_MINUTES_END; running_average_minutes += RUNNING_AVERAGE_MINUTES_STEP) {

			System.out.println("The running average is " + running_average_minutes);

			searchForBestYInterceptInnerLoop(running_average_minutes, bUseWeatherPrediction);

		}

		// printResults(running_average_minutes_best, deployment_threshold_mv_best);

	}

	public void train(boolean bUseWeatherPrediction) {

		searchForBestRunningAverageMinutesAndYInterceptPair(bUseWeatherPrediction);

	}

	private void processOneSample(Workload.WindspeedSample sample, boolean bUseWeatherPrediction) {

		if (bTransitionsLimited) {

			processOneSampleTransitionsLimited(sample, bUseWeatherPrediction);

		} else {

			processOneSampleTransitionsUnlimited(sample, bUseWeatherPrediction);

		}

	}

	private void processOneSampleTransitionsLimited(Workload.WindspeedSample sample, boolean bUseWeatherPrediction) {


		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		double windspeed_knots_average = ra.updateRunningAverage(sample.windspeed_knots, running_average_window_size);

		double windspeed_knots_average_future = raf.updateRunningAverage(sample.windspeed_knots_predicted_one_day,
				running_average_window_size);
		
		boolean bFutureWindspeedUnavailable = (sample.windspeed_knots_predicted_one_day < 0);    
		if (bFutureWindspeedUnavailable) {
			
			bUseWeatherPrediction = false;
			
		}

		if ((harvester.getMinutesVisibleMonthly() < 1) && ((DateStatistics
				.getMinutesInMonthRemaining(sample.date) < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) ||
		// harvester has not yet been visible this month
				(

				getResultingMembershipValueTransitionLimited(windspeed_knots_average, sample, bUseWeatherPrediction,
						windspeed_knots_average_future) >= deployment_threshold_mv

				))) {

			harvester.setMode();

		}

		// check amount of time used per month
		final float FRACTION_VISIBLE_TIME_THRESHOLD = (float) 0.99;
		if (harvester.getFractionVisbilePlusTimeToRetractMonthly(
				ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) > FRACTION_VISIBLE_TIME_THRESHOLD) {

			harvester.resetMode();

		}

		// if within retraction time of end of month, then retract
		if (DateStatistics.getMinutesInMonthRemaining(sample.date) <= harvester.TIME_TO_RETRACT_MINUTES) {

			harvester.resetMode();

		}

		harvester.processMode(sample, ep, false);

	}

	private double not(double in) {

		return (1.0 - in);

	}

	private void processOneSampleTransitionsUnlimited(Workload.WindspeedSample sample, boolean bUseWeatherPrediction) {

		// Determine what control signal to output.
		// Use visibility-time-remaining to control deployment threshold

		double windspeed_knots_average = ra.updateRunningAverage(sample.windspeed_knots, running_average_window_size);
		double windspeed_knots_average_future = raf.updateRunningAverage(sample.windspeed_knots_predicted_one_day,
				running_average_window_size);
		
		boolean bFutureWindspeedUnavailable = (sample.windspeed_knots_predicted_one_day < 0);    
		if (bFutureWindspeedUnavailable) {
			
			bUseWeatherPrediction = false;
			
		}

		// This code must be called every minute when sunset times are being used
		double resultingMembershipValue = getResultingMembershipValueTransitionUnlimited(windspeed_knots_average,
				sample, bUseWeatherPrediction, windspeed_knots_average_future);
		
		/* If near windy and not near quiet hours, then deploy */

		/* Modified retraction threshold for Fuzzy */

		//final int RETRACTION_THRESHOLD_DIFFERENCE = 1;

		boolean bCrispRetractionThresholdReached = false;
		
		lCrispConditionalEvaluated++;
		
		/* "MoreLenientCase" is defined as a windspeed that does not cause retraction when R.T.D. is 1, but
		 *   does cause retraction when R.T.D. is 0.
		 */
		boolean bCrispRetractionThresholdReachedWhenRTDIsZero = (windspeed_knots_average <windy.getLowestWindspeedThatIsWindyKnots());
		boolean bCrispRetractionThresholdReachedWhenRTDIsOne = (windspeed_knots_average <(windy.getLowestWindspeedThatIsWindyKnots()-1));
		
		boolean bMoreLenientCase = !bCrispRetractionThresholdReachedWhenRTDIsOne	&&
				bCrispRetractionThresholdReachedWhenRTDIsZero;

		boolean bRetractionConditionReached = false;
		
		/* Ensure that algorithm meets the minimum requirements of SLA */
		if (((windspeed_knots_average < (windy.getLowestWindspeedThatIsWindyKnots()-RETRACTION_THRESHOLD_DIFFERENCE)))
				|| !(noiseAllowed.bIsNoiseAllowed(sample.date))
				|| (harvester.getFractionVisbilePlusTimeToRetractMonthly(
						ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)) >= 0.99) {

			harvester.resetMode();
			bRetractionConditionReached = true;

		} else // use fuzzy logic to decide deployment and retraction
		{
			
			final double retractionThresholdMembershipValue = deployment_threshold_mv - 0.2;

			if (resultingMembershipValue >= deployment_threshold_mv) {

				harvester.setMode();

			} else if (resultingMembershipValue < retractionThresholdMembershipValue) {
				
				bRetractionConditionReached = true;
				
				//harvester.resetMode();

			}

		}

		if (bMoreLenientCase && !bRetractionConditionReached) {
			
			bEveryMoreLenientCaseRestricted = false;
			
		}
		
		boolean bFuzzyRetractionThresholdReached =  resultingMembershipValue < (deployment_threshold_mv - 0.2);
		if (bMoreLenientCase && !bFuzzyRetractionThresholdReached) {
			
			bEveryMoreLenientCaseReachesFuzzyRetractionThreshold = false;
			
		}

		
		
		harvester.processMode(sample, ep, true);

	}

	private double getResultingMembershipValueTransitionUnlimited(double windspeed_knots_average,
			Workload.WindspeedSample sample, boolean bUseWeatherPrediction, double windspeed_knots_average_future) {
 
		double membershipValueConditional;

		if (bUseWeatherPrediction) {

			// If very windy tomorrow and running out of time, retract
			// -or-
			// Allow deployment if not very windy tomorrow 
			////  -or-
			// if not running out of time
			
			
			membershipValueConditional = Math.max(
					not(windy.getMembershipValueForVeryWindy((int) windspeed_knots_average_future)),
					harvester.getMembershipValueForPlentyOfAllocatedVisibilityMinutesRemainingShiftX(
							ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth
							)
					);

		} else {

			membershipValueConditional = 1;

		}

		final int MINUTES_BEFORE_QUIET_HOURS_X_INTERCEPT = 120; // upgrade: set during training

		// If windy and (if not approaching quiet hours or if fraction of time spent
		// stowed is low)
		double resultingMembershipValue = Math.min(membershipValueConditional,

				windy.getMembershipValueForWindy((int) windspeed_knots_average));
		
				not(noiseAllowed.getMembershipValueForApproachingQuietHours(sample.date,
						MINUTES_BEFORE_QUIET_HOURS_X_INTERCEPT));

				Math.min(windy.getMembershipValueForWindy((int) windspeed_knots_average),
				Math.max(
						not(noiseAllowed.getMembershipValueForApproachingQuietHours(sample.date,
								MINUTES_BEFORE_QUIET_HOURS_X_INTERCEPT)),
						harvester.getMembershipValueForPlentyOfAllocatedVisibilityMinutesRemainingShiftX(
								ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)));
				
				
//			noPredictionResult = 	Math.min(windy.getMembershipValueForWindy((int) windspeed_knots_average),
//						Math.max(
//								not(noiseAllowed.getMembershipValueForApproachingQuietHours(sample.date,
//										MINUTES_BEFORE_QUIET_HOURS_X_INTERCEPT)),
//								harvester.getMembershipValueForPlentyOfAllocatedVisibilityMinutesRemaining(
//										ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)))

//		);

//		if (membershipValueConditional < noPredictionResult) {
//			System.out.println("Prediction had influence.");
//		}  else {
//			System.out.println("No influence.");			
//		}
		
		return (resultingMembershipValue);

	}

	private double getResultingMembershipValueTransitionLimited(double windspeed_knots_average,
			Workload.WindspeedSample sample, boolean bUseWeatherPrediction, double windspeed_knots_average_future) {

		double windspeed_knots_average_to_use;


		if (bUseWeatherPrediction) {

			// must be windy today and windy to-morrow
			windspeed_knots_average_to_use = Math.min(windspeed_knots_average, windspeed_knots_average_future);

		} else {

			windspeed_knots_average_to_use = windspeed_knots_average;

		}

		double resultingMembershipValue = Math.max(
				windy.getMembershipValueForWindy((int) windspeed_knots_average_to_use),
				DateStatistics.getMembershipValueForApproachingUseItOrLoseItPoint(sample.date, harvester, ws));

		return (resultingMembershipValue);

	}

	private void reset() {

		harvester.resetAll(ep);
		noiseAllowed.reset();
		noiseAllowedFutureRetractionTime.reset();

		lCrispConditionalEvaluated = 0;
		lCrispRetractionThresholdReached = 0;
		lFuzzyStricterThanCrisp  = 0;
		lCrispFoundNoReasonToRetract = 0;
		bEveryMoreLenientCaseRestricted = true;
		bEveryMoreLenientCaseReachesFuzzyRetractionThreshold = true;
	
		
	}

	private double processOneIterationOfTestingSamples(int[] running_avg_size_minutes,
			double[] deployment_threshold_mv_best, boolean bUseWeatherPrediction) {

		ra = new RunningAverage(running_avg_size_minutes);
		raf = new RunningAverage(running_avg_size_minutes);

		Workload.WindspeedSample sample;

		ws.openTestingFile();

		reset();

		while ((sample = ws.getNextWindspeedSampleTesting()) != null) {

			deployment_threshold_mv = deployment_threshold_mv_best[sample.date.getMonthValue()]; // get best y-intercept
																									// for month
			running_average_window_size = running_avg_size_minutes[sample.date.getMonthValue()];// bet best
																								// running-average-window-size
																								// for month
			processOneSample(sample, bUseWeatherPrediction);

		}
		// finalize iteration
		harvester.addToMonthlyStatisticsListForFINALMonth();
		printResults(running_avg_size_minutes, deployment_threshold_mv_best);

		return (harvester.getEnergyHarvestedKilowattHours());

	}

	public void printResults(int[] running_avg_size_minutes, double[] local_deployment_threshold_mv_array) {

		Results.printResultsFuzzy(station, harvester, ep, running_avg_size_minutes,
				local_deployment_threshold_mv_array);
		
		System.out.println("FuzzyVsCrisp: " + station + "," +
										RETRACTION_THRESHOLD_DIFFERENCE  + "," +
										bEveryMoreLenientCaseRestricted + "," +
										bEveryMoreLenientCaseReachesFuzzyRetractionThreshold);

	}

	public void testing(boolean bUseWeatherPrediction) {

		processOneIterationOfTestingSamples(running_average_minutes_best, deployment_threshold_mv_best,
				bUseWeatherPrediction);
		
		processOneIterationOfTestingSamples(running_average_minutes_best_market, deployment_threshold_mv_best_market,
				bUseWeatherPrediction);


	}

}
