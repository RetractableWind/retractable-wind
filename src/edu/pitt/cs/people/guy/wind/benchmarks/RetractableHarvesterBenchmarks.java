package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

import edu.pitt.cs.people.guy.wind.benchmarks.Workload;
import edu.pitt.cs.people.guy.wind.benchmarks.RunningAverage;
import edu.pitt.cs.people.guy.wind.benchmarks.PowerCurve;
import edu.pitt.cs.people.guy.wind.benchmarks.HarvesterModel;
//import edu.pitt.cs.people.guy.wind.benchmarks.CheckContinuity;
//import edu.pitt.cs.people.guy.wind.benchmarks.Fuzzy5;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.io.InputStreamReader;
import java.text.DecimalFormat;



public class RetractableHarvesterBenchmarks {

	/* Deploy or retract must be called every minute */
	class ControlAlgorithm {

		int DEPLOYMENT_THRESHOLD;
		int RETRACTION_THRESHOLD;

		final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_PREFIX = "/training";
		final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_POSTFIX = "2004-2008imembershipFunctionNotWindy.out";

		HarvesterModel harvester;

		RunningAverage running_average = null;

		Workload ws = null;

		public ControlAlgorithm(String localStation, HarvesterModel localHarvester, Workload localWorkload) {

			final float LAMBDA = (float) 0.5;
			int greatest_value_of_lambda_cut = 0;

			final String dir = System.getProperty("user.dir");
			System.out.println("current dir = " + dir);

			final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE = MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_PREFIX
					+ localStation + MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_POSTFIX;
			harvester = localHarvester;

			ws = localWorkload;

			float membershipValue = 1;

			/* Get lambda-cut bound of NOT WINDY */
			InputStream inputStream = this.getClass().getResourceAsStream(MEMBERSHIP_FUNCTION_NOT_WINDY_FILE);
			
			if (inputStream != null) {
				
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
					System.out.println("I have found the membership file for NOT WINDY.");
					String line = br.readLine(); // read table header
					// read values
					greatest_value_of_lambda_cut = -1;
					while (((line = br.readLine()) != null) && (membershipValue > LAMBDA)) {
						// use comma as separator
						String[] cols = line.split("\\s+");
						greatest_value_of_lambda_cut++;
						membershipValue = Float.parseFloat(cols[1]);
						System.out.println("Windspeed (knots) " + cols[0] + ", Membership Value =" + cols[1]);
					}
					br.close();
					greatest_value_of_lambda_cut--;
					System.out.println("The greatest windspeed in the lamda cut is " + greatest_value_of_lambda_cut);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			} else {
				System.out.println("membership file '" + MEMBERSHIP_FUNCTION_NOT_WINDY_FILE + "' not found in the classpath");
				System.exit(0);
				//throw new FileNotFoundException("membership file '" + MEMBERSHIP_FUNCTION_NOT_WINDY_FILE + "' not found in the classpath");
			}

			DEPLOYMENT_THRESHOLD = greatest_value_of_lambda_cut + 1;
			RETRACTION_THRESHOLD = greatest_value_of_lambda_cut - 1;

			System.out.println("Deployment Threshold (knots): " + DEPLOYMENT_THRESHOLD
					+ ", Retraction Threshold (knots): " + RETRACTION_THRESHOLD);

			/* initialize running average */
			// running_average = new RunningAverage(findRunningAverage()); /* TODO find the
			// best running average */
			//running_average = new RunningAverage(20);
		}

		/* Find reasonable running average */
		/*
		 * private int findRunningAverage() {
		 * 
		 * 
		 * // Loop through running average from 1 to 1440
		 * 
		 * 
		 * 
		 * 
		 * 
		 * }
		 */

		public void timestep(Workload.WindspeedSample sample) {

			//double average = running_average.updateRunningAverage(sample.windspeed_knots);
			//System.out.println(sample.windspeed_knots + "," + average);

		}

	}

	/* updateStatistics must be called every minute */
	public static void main(String[] args) {

		//String[] parameter = args[0].split("\\s+");
		//String station = parameter[0];
		String station_argument = args[0];
		
		//String station = "KBOS";
		System.out.println("Station_argument: " + station_argument);

		float lambda = Float.parseFloat(args[1]);
		System.out.println("lambda: " + lambda);

		boolean bUseWeatherPrediction = Boolean.parseBoolean(args[2]);
		System.out.println("bUseWeatherPrediction: " + bUseWeatherPrediction);

		boolean bTransitionLimited = Boolean.parseBoolean(args[3]);
		System.out.println("bTransitionLimited: " + bTransitionLimited);


		char cAlgorithm = args[4].charAt(0);    
		System.out.println("cAlgorithm: " + cAlgorithm);


		// Build set of stations through which to iterate
		final String DO_ALL_STATIONS_STRING = "ALL";
		String stationArray[];
		if (station_argument.equals(DO_ALL_STATIONS_STRING)) {
		
		    stationArray = new String[]{"KATL",
						"KBOS",
						"KBWI",
						"KCLE",
						"KCLT",
						"KCVG",
						"KDCA",
						"KDEN",
						"KDFW",
						"KDTW",
						"KEUG",
						"KIAH",
						"KLAS",
						"KLAX",
						"KLGA",
						"KMCI",
						"KMCO",
						"KMSP",
						"KORD",
						"KPHL",
						"KPHX",
						"KPIT",
						"KSAC",
						"KSAN",
						"KSAT",
						"KSEA",
						"KSFO",
						"KSMX",
						"KSTL",
						"KTPA"};
		} else {

		    stationArray = new String[]{station_argument};

		}


		for (String station : stationArray){ 

		    System.out.println("Station: " + station);

		RetractableHarvesterBenchmarks rhs = new RetractableHarvesterBenchmarks();

		Workload ws = new Workload(station, true);
		System.out.println("iUsedAllItsAllocatedVisibilityMinutesPerMonth: " + ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
		System.out.println("Visibility Allocation Minutes:" + ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);

		HarvesterModel hm = new HarvesterModel(ws.iDeploymentTimeMinimumMinutes, ws.iRetractionTimeMinimumMinutes);


	
		
//		ControlAlgorithm ca = rhs.new ControlAlgorithm(station, hm, ws);
//
//		/* loop */
//		int j = 0;
//		Workload.WindspeedSample sample;
//		while ((sample = ws.getNextWindspeedSampleTesting()) != null) {
//		
//			ca.timestep(sample);
//		 	//System.out.println(sample.windspeed_knots);
//		 	if (j++>10) break;
//		
//		}
		//ca.timestep();

//		for (int i=0; i<25; i++) {
//		 System.out.println("Windspeed=" + i + " Deployment Energy=" +
//		 hm.getDeploymentEnergyUsedPerMinuteKwh(1, i));
//		}

//		OptimumAlgorithm oa = new OptimumAlgorithm(station, hm, ws);
//		oa.findOptimumPath(); 
		
		//FuzzyAlgorithm fa = new FuzzyAlgorithm(station, hm, ws);
		//int runningAverageSize = fa.findRunningAverageSize(); //Use training data to find running average size
		//int runningAverageSize = 1;
		//fa.runSimulationUsingPrediction(runningAverageSize);
		//System.out.println("Fraction of Energy Harvested Over Energy Available " +
		//		hm.getFractionOfEnergyHarvestedOverEnergyAvailable());
		//System.out.println("End of Fuzzy Test.");
		
//		OptimumAlgorithm2StateTransitionsPerMonthMaximum oa =
//			new OptimumAlgorithm2StateTransitionsPerMonthMaximum(station, hm, ws);
//
//		oa.findOptimumPath();     /* */
		

		
		
//		AppendRunningAverageToWorkload af =
//				new AppendRunningAverageToWorkload(station,
//						hm,
//						ws,
//						60,
//						true);
		
//		AppendFutureValuesToWorkload3 fv =
//				new AppendFutureValuesToWorkload3(station,
//				hm,
//				ws,
//				24 * 60,
//				true);
		
	
		

		

//		PermanentlyDeployed pd =
//				new 	PermanentlyDeployed(station,
//						hm,
//						ws);
//		DecimalFormat df = new DecimalFormat("#.#########");
//		System.out.print("11/6/2018: " + station + ": Total Energy Harvested GwH: Training " + 
//				df.format(pd.findEnergyHarvestedKwhTraining()/1000000));
//				
//		
//		pd =
//				new 	PermanentlyDeployed(station,
//						hm,
//						ws);
//		System.out.println("  11/6/2018: " + station + ": Total Energy Harvested GwH: Testing " + 
//				df.format(pd.findEnergyHarvestedKwhTesting()/1000000));
		
//		CheckForOverwinds co = new CheckForOverwinds(station, hm, ws);
//		
//		System.out.println("9/25/2018: " + station);
		
// -----

		// Test
//		CheckContinuity cc  = new CheckContinuity(station,
//				hm,
//				ws,
//				1,
//				true);
		 
		



		switch(cAlgorithm) {
		    case 'a':
			System.out.println("Aging");

		Aging ag = new Aging(station, hm, ws, bTransitionLimited, lambda);
		System.out.println("Transition limited :" + bTransitionLimited);
		

		System.out.println("bUseWeatherPrediction: " + bUseWeatherPrediction);
		
		ag.train(bUseWeatherPrediction);
		System.out.println("Beginning testing mode:");
		hm.listMonthlyStatistics.clear();
		ag.testing(bUseWeatherPrediction);

			break;

		case 'f':

		    System.out.println("Fuzzy");

		    Fuzzy5 f5 = new Fuzzy5(station, hm, ws, bTransitionLimited, lambda);
		    System.out.println("Transition limited :" + bTransitionLimited);
		    
		    System.out.println("bUseWeatherPrediction: " + bUseWeatherPrediction);
		    
		    f5.train(bUseWeatherPrediction);
		    System.out.println("Beginning testing mode:");
		    hm.listMonthlyStatistics.clear();
		    f5.testing(bUseWeatherPrediction);
		    		    
		    break;

		case 's':

		    Static st = new Static(station, hm, ws, bTransitionLimited, lambda);
		    System.out.println("Transition limited :" + bTransitionLimited);
		
		    System.out.println("bUseWeatherPrediction:" + bUseWeatherPrediction);
		    st.train(bUseWeatherPrediction);
		    System.out.println("Beginning testing mode:");
		    hm.listMonthlyStatistics.clear();
		    st.testing(bUseWeatherPrediction);

		    break;

		default:

		    System.out.println("Algorithm not found corresponding to " +
				       cAlgorithm);

		    break;
		    }

		}
		
//		Windy wind = new Windy(station);
//		System.out.println("Windy: & " + station + " & "  +
//				wind.getLowestWindspeedThatIsWindyKnots());	
		 
//		int[]  running_average_minutes_array = new int[1];
//		running_average_minutes_array[0] = 10;
//		RunningAverage ra = new RunningAverage(running_average_minutes_array);
//		for (int i=0; i<4; i++) {
//			
//			System.out.println(i + "," + ra.updateRunningAverage(10, 2));
//			
//		}
//
//		for (int i=0; i<10; i++) {
//			
//			System.out.println(i + "," + ra.updateRunningAverage(10, 10));
//			
//		}

		
		
//		NoiseAllowed na = new NoiseAllowed("KSTL"); 
//		na.getFields();
  //		//na.getSunsetTimes();
//		LocalDateTime ldt = LocalDateTime.of(2004, Month.JANUARY, 1, 0, 0);
//		System.out.println("The index is " + na.getIndexOfLocalDateTimeSunsetStandardTime(ldt));
 
		
		//InterpolationStatistics is = new InterpolationStatistics(station, ws);

		
  		// st.testing(bUseWeatherPrediction,
		// 	   7,
		// 		31,
		// 		7,
		// 		61);

	       // 		int settings[] = {0, 0, 0, 0};
	       //  switch(station) 
	       //  { 
	       //  case("KATL"): settings = new int[] {7,31,7,61};
	       //  break;
	       //  case("KBOS"): settings = new int[] {9,31,9,61};
	       //  break;
	       //  case("KBWI"): settings = new int[] {7,31,7,61};
	       //  break;
	       //  case("KCLE"): settings = new int[] {8,31,8,61};
	       //  break;
	       //  case("KCLT"): settings = new int[] {5,1,5,91};
	       //  break;
	       //  case("KCVG"): settings = new int[] {7,31,7,61};
	       //  break;
	       //  case("KDCA"): settings = new int[] {7,1,7,61};
	       //  break;
	       //  case("KDEN"): settings = new int[] {8,1,8,121};
	       //  break;
	       //  case("KDFW"): settings = new int[] {9,31,9,31};
	       //  break;
	       //  case("KDTW"): settings = new int[] {8,31,8,91};
	       //  break;
	       //  case("KEUG"): settings = new int[] {6,1,6,91};
	       //  break;
	       //  case("KIAH"): settings = new int[] {7,1,7,61};
	       //  break;
	       //  case("KLAS"): settings = new int[] {8,1,8,31};
	       //  break;
	       //  case("KLAX"): settings = new int[] {7,1,7,61};
	       //  break;
	       //  case("KLGA"): settings = new int[] {9,31,9,61};
	       //  break;
	       //  case("KMCI"): settings = new int[] {8,1,8,31};
	       //  break;
	       //  case("KMCO"): settings = new int[] {7,1,7,61};
	       //  break;
	       //  case("KMSP"): settings = new int[] {8,31,8,91};
	       //  break;
	       //  case("KORD"): settings = new int[] {8,31,8,91};
	       //  break;
	       //  case("KPHL"): settings = new int[] {8,31,8,61};
	       //  break;
	       //  case("KPHX"): settings = new int[] {6,31,6,121};
	       //  break;
	       //  case("KPIT"): settings = new int[] {7,1,7,61};
	       //  break;
	       //  case("KSAC"): settings = new int[] {6,1,6,91};
	       //  break;
	       //  case("KSAN"): settings = new int[] {5,1,5,61};
	       //  break;
	       //  case("KSAT"): settings = new int[] {7,1,7,121};
	       //  break;
	       //  case("KSEA"): settings = new int[] {6,1,6,121};
	       //  break;
	       //  case("KSFO"): settings = new int[] {10,1,10,61};
	       //  break;
	       //  case("KSMX"): settings = new int[] {8,1,8,61};
	       //  break;
	       //  case("KSTL"): settings = new int[] {7,1,7,91};
	       //  break;
	       //  case("KTPA"): settings = new int[] {6,1,6,91};
	       //  break;
	       // default: 
	       //          System.out.println("station not found"); 
	       //          System.exit(0);
	       //  } 	
		
	       // 	st.testing(bUseWeatherPrediction,
	       // 			settings[0],
	       // 			settings[1],
	       // 			settings[2],
	       // 			settings[3]);

		

//		boolean bTransitionsLimited = false;
//		Fuzzy2 fa = new Fuzzy2(station, hm, ws, bTransitionsLimited);
//		int runningAverageSize = fa.findRunningAverageSize(); //Use training data to find running average size
//		int runningAverageSize = 1;
//		//fa.runSimulationUsingPrediction(runningAverageSize);
	
//		boolean bUseWeatherPrediction = false;
//		fa.testing(bUseWeatherPrediction);
		//System.out.println("Fraction of Energy Harvested Over Energy Available " +
		//		hm.getFractionOfEnergyHarvestedOverEnergyAvailable());
		//System.out.println("End of Fuzzy Test.");

		

	}
}
