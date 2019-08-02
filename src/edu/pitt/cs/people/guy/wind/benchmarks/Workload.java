package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Random;

class Workload {

	/* The workload has these components */
	/* 1. Station */
	/* 2. SLA */

	String station;

	public boolean bNotWindy;
	public boolean bDuringNightlyVisibilityBan;
	public boolean bTooWindy;
	public int iUsedAllItsAllocatedVisibilityMinutesPerMonth;

	/* TODO: Harvester Model is its own class */
	public int iDeploymentTimeMinimumMinutes; /* Inverse of speed */
	public int iRetractionTimeMinimumMinutes; /* Inverse of speed */

	/* Months of year Upgrade: allow one month */
	/* TODO: Function defining incentive and penalties its own class */
	private BufferedReader readerWindspeedsTraining = null;
	private BufferedReader readerWindspeedsTesting = null;

	/* For Predictive algorithms */	
	private BufferedReader readerWindspeedsTrainingFuture = null;
	private BufferedReader readerWindspeedsTestingFuture = null;

	String TESTING_WINDSPEED_FILENAME;
	String TRAINING_WINDSPEED_FILENAME;
	String HOURLY_ENERGY_PRICE_FILENAME;
	
	private String fileHeader;
	
	Random random = new Random(); // in case prediction is being used

	/* constructor */
	public Workload(String startStation, Boolean bAlsoUsePrediction) {

		station = startStation;

		/* Get the parameters from the properties file */
		/*
		 * The following method is derived from the one posted at
		 * http://crunchify.com/java-properties-file-how-to-read-config-properties-
		 * values-in-java/ public String getPropValues() throws IOException {
		 * 
		 * /* open parameter file
		 */

		InputStream inputStream = null;

		try {

			Properties prop = new Properties();

			final String currpath = System.getProperty("user.dir");
			System.out.println("abc: The current path is " + currpath);

			String propFileName = "/RetractableHarvesterBenchmarks.properties";

			// inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
			inputStream = this.getClass().getResourceAsStream(propFileName);

			if (inputStream != null) {
				prop.load(inputStream);
				System.out.println("I have found the property file.");
			} else {
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			}

			// get the property value and print it out
			bNotWindy = prop.getProperty("bNotWindy").equals("true");

			bDuringNightlyVisibilityBan = prop.getProperty("bDuringNightlyVisibilityBan").equals("true");

			bTooWindy = prop.getProperty("bTooWindy").equals("true");

			iUsedAllItsAllocatedVisibilityMinutesPerMonth = Integer
					.parseInt(prop.getProperty("iUsedAllItsAllocatedVisibilityMinutesPerMonth"));

			/* TODO: Harvester Model is its own class */

			iDeploymentTimeMinimumMinutes = Integer.parseInt(prop.getProperty("iDeploymentTimeMinimumMinutes"));

			iRetractionTimeMinimumMinutes = Integer.parseInt(prop.getProperty("iRetractionTimeMinimumMinutes"));

			final String TESTING_WINDSPEED_FILENAME_ENDING = prop.getProperty("TESTING_WINDSPEED_FILENAME_ENDING");
			final String TRAINING_WINDSPEED_FILENAME_ENDING = prop
					.getProperty("TRAINING_WINDSPEED_FILENAME_ENDING");

			TRAINING_WINDSPEED_FILENAME = "/training" + startStation + TRAINING_WINDSPEED_FILENAME_ENDING;
			TESTING_WINDSPEED_FILENAME = "/testing" + startStation + TESTING_WINDSPEED_FILENAME_ENDING;

			HOURLY_ENERGY_PRICE_FILENAME = prop.getProperty("ENERGY_PRICE_FILENAME");
			
			System.out.println("The training windspeed filename is " + TRAINING_WINDSPEED_FILENAME);
			System.out.println("The testing windspeed filename is " + TESTING_WINDSPEED_FILENAME);

			
			System.out.println("Yes, the station is " + startStation);

		} catch (Exception e) {
			System.out.println("Exception: " + e);
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

			}
		}


		openTrainingFile();
		
		openTestingFile();
		
		//Upgrade: Add flag to only open if
		if (bAlsoUsePrediction) {
			
			System.out.println("Warning: The bAlsoUsePrediction no longer has any effect");
			
						
		}
		

	}


	public String getTRAINING_WINDSPEED_FILENAME() {
		
		return(TRAINING_WINDSPEED_FILENAME);
		
	}
	
	
	// Set equal to readerWindspeedsTraining (for e.g.)
	// Pass TRAINING_WINDSPEED_FILENAME (for e.g.) to filename
	public BufferedReader openWindspeedFile(String filename) {
		
		BufferedReader bufferedReader = null;
		
		/* Open the files of testing windspeed samples */
		/*
		 * The following method is derived from the one posted at
		 * http://crunchify.com/java-properties-file-how-to-read-config-properties-
		 * values-in-java/ public String getPropValues() throws IOException {
		 * 
		 * /* open the windspeed file
		 */
		/* Open file that has minute-by-minute windspeed samples */
		try {
			// readerWindspeedsTraining = new BufferedReader(new
			// FileReader(TRAINING_WINDSPEED_FILENAME));
			InputStream inputStream = this.getClass().getResourceAsStream(filename);

			if (inputStream == null) {
				 System.out.println("Null for " + filename);

			} else {

				//System.out.println("Success: " + filename + " found.");

			}
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			if (bufferedReader != null) {
				// advance past header
				if ((fileHeader = bufferedReader.readLine()) != null) {
					//System.out.println("The header of the training file is " + fileHeader);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		//System.out.println("The readerWindspeedsTraining is " + bufferedReader);

		return(bufferedReader);		

	}

	
	
	public void openTrainingFile() {
		
		// Set readerWindspeedsTraining (for e.g.)
		// Pass TRAINING_WINDSPEED_FILENAME (for e.g.) to filename
		 readerWindspeedsTraining = openWindspeedFile(TRAINING_WINDSPEED_FILENAME);
		return;
		
	}
	
	public void openTestingFile() {
		
		readerWindspeedsTesting = openWindspeedFile(TESTING_WINDSPEED_FILENAME);
		return;
		
	}


	public void openTrainingFileFuture(int minutesToLookAhead) {
		
		// Set readerWindspeedsTraining (for e.g.)
		// Pass TRAINING_WINDSPEED_FILENAME (for e.g.) to filename
		readerWindspeedsTrainingFuture = openWindspeedFile(TRAINING_WINDSPEED_FILENAME);
		
		// advance so many minutes into the future
		for (int i=0; i<minutesToLookAhead; i++) {
			
			getNextLinefromTrainingDataFuture();
			
		}
	
		return;
		
	}
	
	public void openTestingFileFuture(int minutesToLookAhead) {
		
		readerWindspeedsTestingFuture = openWindspeedFile(TESTING_WINDSPEED_FILENAME);
		
		// advance so many minutes into the future
		for (int i=0; i<minutesToLookAhead; i++) {
			
			getNextLinefromTestingDataFuture();
			
		}
		
		
		return;
		
	}

	
	
//	public void openTrainingFileOriginal() {
//				
//		/* Open the files of testing windspeed samples */
//		/*
//		 * The following method is derived from the one posted at
//		 * http://crunchify.com/java-properties-file-how-to-read-config-properties-
//		 * values-in-java/ public String getPropValues() throws IOException {
//		 * 
//		 * /* open the windspeed file
//		 */
//		/* Open file that has minute-by-minute windspeed samples */
//		try {
//			// readerWindspeedsTraining = new BufferedReader(new
//			// FileReader(TRAINING_WINDSPEED_FILENAME));
//			InputStream inTraining = this.getClass().getResourceAsStream(TRAINING_WINDSPEED_FILENAME);
//
//			if (inTraining == null) {
//				System.out.println("Null for " + TRAINING_WINDSPEED_FILENAME);
//
//			} else {
//
//				//System.out.println("Success: " + TRAINING_WINDSPEED_FILENAME + " found.");
//
//			}
//			readerWindspeedsTraining = new BufferedReader(new InputStreamReader(inTraining));
//			if (readerWindspeedsTraining != null) {
//				// advance past header
//				String line;
//				if ((line = readerWindspeedsTraining.readLine()) != null) {
//					//System.out.println("The header of the training file is " + line);
//				}
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//	}
//
//	
//	public void openTestingFileOriginal() {		
//	
//	try {
//		// readerWindspeedsTesting = new BufferedReader(new
//		// FileReader(TESTING_WINDSPEED_FILENAME));
//		InputStream inTesting = this.getClass().getResourceAsStream(TESTING_WINDSPEED_FILENAME);
//
//		if (inTesting == null) {
//			System.out.println("Null for " + TESTING_WINDSPEED_FILENAME);
//
//		} else {
//
//			System.out.println("Success: " + TESTING_WINDSPEED_FILENAME + " found.");
//
//		}
//		readerWindspeedsTesting = new BufferedReader(new InputStreamReader(inTesting));
//		if (readerWindspeedsTesting != null) {
//			// advance past header
//			String line;
//			if ((line = readerWindspeedsTesting.readLine()) != null) {
//				System.out.println("The header of the testing file is " + line);
//			}
//		}
//
//	} catch (FileNotFoundException e) {
//		e.printStackTrace();
//	} catch (IOException e) {
//		e.printStackTrace();
//	}
//}
//		
	
	public int getDeploymentTimeMinimumMinutes() {

		return (iDeploymentTimeMinimumMinutes);

	}

	public int getRetractionTimeMinimumMinutes() {

		return (iRetractionTimeMinimumMinutes);

	}
	
	
	public void reopenReaderTrain( ) {
		
		try {
			readerWindspeedsTraining.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		openTrainingFile();
		
	}
	
	public void reopenReaderTest( ) {
		
		try {
			readerWindspeedsTesting.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		openTestingFile();
		
	}

	/* Close training file */
	public void closeReaderTrain() {

		try {
			readerWindspeedsTraining.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/* Close testing file */
	public void closeReaderTest() {

		try {
			readerWindspeedsTesting.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private String getNextLinefromTrainingData() {

		String line = null;
		try {

			line = readerWindspeedsTraining.readLine();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return (line);

	}

	private String getNextLinefromTestingData() {

		String line = null;
		try {

			line = readerWindspeedsTesting.readLine();
			//System.out.println("The next line from the testing file is " + line);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return (line);

	}

	private String getNextLinefromTrainingDataFuture() {

		String line = null;
		try {

			line = readerWindspeedsTrainingFuture.readLine();

		} catch (IOException e) {
			e.printStackTrace();
		}

		return (line);

	}

	private String getNextLinefromTestingDataFuture() {

		String line = null;
		try {

			line = readerWindspeedsTestingFuture.readLine();
			//System.out.println("The next line from the testing file is " + line);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return (line);

	}

	
	class WindspeedSample {

		// Process the time
		private int year;
		private int month;
		private int day;
		private int time;
		private int hourOfDay;
		private int hourOfDay_UTC;
		private int minute;
		private int minuteOfDay; /* 1440 minutes total */
		int windspeed_knots;
		int windspeed_knots_predicted_one_day; 
		LocalDateTime date;
		Boolean BDeployOrRemainDeployed = null;
		boolean bActual = false;

		public WindspeedSample(String line) {

			final int INTERPOLATED_COLUMN_INDEX = 2;			
			final int WINDSPEED_PREDICTED_COLUMN_INDEX = 3;			
			final int TRANSITION_LIMITED_IDEAL_COLUMN_INDEX = 4;
			final char DEPLOY_OR_REMAIN_DEPLOYED_SYMBOL = 'd';
			
			/* parse line */
			String[] cols = line.split(",");
			String timestamp = cols[0];

			// Process the time (convert to local time, which includes DST*)
			year = Integer.parseInt(timestamp.substring(0, 4));
			month = Integer.parseInt(timestamp.substring(4, 6));
			day = Integer.parseInt(timestamp.substring(6, 8));

			hourOfDay = Integer.parseInt(timestamp.substring(8, 10));
			minute = Integer.parseInt(timestamp.substring(10, 12));

			hourOfDay_UTC = Integer.parseInt(timestamp.substring(12, 14));

			windspeed_knots = Integer.parseInt(cols[1]);
			windspeed_knots_predicted_one_day = Integer.parseInt(cols[WINDSPEED_PREDICTED_COLUMN_INDEX]);
			
			bActual = (cols[INTERPOLATED_COLUMN_INDEX].charAt(0) == 'a');
			
			if (cols.length > TRANSITION_LIMITED_IDEAL_COLUMN_INDEX) {
				
				BDeployOrRemainDeployed = (cols[TRANSITION_LIMITED_IDEAL_COLUMN_INDEX].charAt(0) ==
						DEPLOY_OR_REMAIN_DEPLOYED_SYMBOL);
				
			}

			minuteOfDay = hourOfDay * 60 + minute;

			date = LocalDateTime.of(year, month, day, hourOfDay, minute, 0, 0);
			// System.out.println("Before adjustment, if any, " + date);

//			// Convert to Daylight Savings Time if applicable.
//			// Because the training data is used to train for 2009-2014,
//			// DST is calculated for all years using the 2007-and-after law:
//			// 2nd Sunday in March and 1st Sunday in November.
//			int secondSundayInMarch = 0;
//			int firstSundayInNovember = 0;
//			switch (year) {
//			case 2004: // 2004 Mar. 14 November 7
//				secondSundayInMarch = 14;
//				firstSundayInNovember = 7;
//				break;
//			case 2005: // 2005 Mar. 13 November 6
//				secondSundayInMarch = 13;
//				firstSundayInNovember = 6;
//				break;
//			case 2006: // 2006 Mar. 13 November 5
//				secondSundayInMarch = 13;
//				firstSundayInNovember = 5;
//				break;
//			case 2007: // 2007 Mar. 11 November 4
//				secondSundayInMarch = 11;
//				firstSundayInNovember = 4;
//				break;
//			case 2008: // 2008 Mar. 9 November 2
//				secondSundayInMarch = 9;
//				firstSundayInNovember = 2;
//				break;
//			case 2009: // 2009 Mar. 8 November 1
//				secondSundayInMarch = 8;
//				firstSundayInNovember = 1;
//				break;
//			case 2010: // 2010 Mar. 14 November 7
//				secondSundayInMarch = 14;
//				firstSundayInNovember = 7;
//				break;
//			case 2011: // 2011 Mar. 13 November 6
//				secondSundayInMarch = 13;
//				firstSundayInNovember = 6;
//				break;
//			case 2012: // 2012 Mar. 11 November 4
//				secondSundayInMarch = 11;
//				firstSundayInNovember = 4;
//				break;
//			case 2013: // 2013 Mar. 10 November 3
//				secondSundayInMarch = 10;
//				firstSundayInNovember = 3;
//				break;
//			case 2014: // 2014 Mar. 9 November 2
//				secondSundayInMarch = 9;
//				firstSundayInNovember = 2;
//				break;
//			}
//
//			assert (secondSundayInMarch > 0 && firstSundayInNovember > 0) : "Date not supported";
//
//			Boolean bIsDaylightSavingsTime = true;
//			switch (month) {
//			case 1:
//				// fall through
//			case 2:
//				// fall through
//			case 12:
//				bIsDaylightSavingsTime = false;
//				break;
//			case 3:
//				bIsDaylightSavingsTime = ((day >= secondSundayInMarch) && (hourOfDay >= 2));
//				break;
//			case 11:
//				bIsDaylightSavingsTime = ((day <= firstSundayInNovember) && (hourOfDay < 2));
//				break;
//			}
//
//			if (bIsDaylightSavingsTime) {
//				// add an hour
//				
//				date = date.plusHours(1);
//				//System.out.println("After adjustment, if any, " + date);
//
//			}
			
			
		}

		// dummy constructor to act as temporary place holder
		public WindspeedSample() {
		
			
			
		}
		


	}
	
	public WindspeedSample getNextWindspeedSampleTesting() {

		WindspeedSample sample = null;
		String line = getNextLinefromTestingData();
		if (line != null) {
			sample = new WindspeedSample(line);
		}

		return (sample);

	}

	public WindspeedSample getNextWindspeedSampleTraining() {

		WindspeedSample sample = null;
		String line = getNextLinefromTrainingData();
		if (line != null) {
			sample = new WindspeedSample(line);
		}

		return (sample);

	}

	
	

	private int accountForPredictionAccuracy(int originalWindspeed, double windspeedMeadKnots) {
		
		//TODO: Find paper that describes accuracy of 1-hour windspeed
		// then modify prediction accuracy
		
		final double RELATIVE_STD_DEV_OF_ERROR = 0.3; // Number from sde of 
																								//  Hilkenbrook at 24 hours
		// from  the paper "OntheUncertaintyofWindPower Predictions --- Analysis of the ForecastAccuracyandStatistical DistributionofErrors"
		
		double std_deviation_of_error = windspeedMeadKnots * RELATIVE_STD_DEV_OF_ERROR;
		
		int candidateFutureWindpseed = ((int) (originalWindspeed + (random.nextGaussian() * std_deviation_of_error)));
		int iFutureWindpeed = candidateFutureWindpseed  < 0 ? 0 : candidateFutureWindpseed;
		
		return (iFutureWindpeed);
				
	}
	
	public WindspeedSample getNextWindspeedSampleTestingPredicted(double meanWindspeedKnots) {

		WindspeedSample sample = null;
		String line = getNextLinefromTestingDataFuture();
		if (line != null) {
			sample = new WindspeedSample(line);

			// Modify predicted sample to reflect accuracy range
			sample.windspeed_knots = accountForPredictionAccuracy(sample.windspeed_knots, meanWindspeedKnots);
		
		}
		
		return (sample);

	}

	public WindspeedSample getNextWindspeedSampleTrainingPredicted(double meanWindspeedKnots) {

		WindspeedSample sample = null;
		String line = getNextLinefromTrainingDataFuture();
		if (line != null) {
			
			sample = new WindspeedSample(line);

		// Modify predicted sample to reflect accuracy range
		sample.windspeed_knots = accountForPredictionAccuracy(sample.windspeed_knots, meanWindspeedKnots);

		}
		
		return (sample);

	}
	
	
	/******************************************************************************/
	/* Code for creating a copy of the training file and appending another column */
	/******************************************************************************/
	
	
	BufferedWriter bufferedWriterForAppending;
	
	public void prepareToCopyAndAppend(String headerToAppend, boolean bTraining) {
		
		bufferedWriterForAppending = readyForCopyAndAppending(bTraining); // create destination file

		if (bTraining) {
			reopenReaderTrain( );
		} else {
			reopenReaderTest( );
		}
			
		try {
			bufferedWriterForAppending.write(fileHeader + "," + headerToAppend + "\n");
		} catch (IOException e) {

			e.printStackTrace();
		}

	}
	
	private int timestepInAppendingFileTraining = 0;
	private int timestepInAppendingFileTesting = 0;

	public String copyFileAppendColumnTraining(String valueToAppend) {
		
		String line;
		if ((line = getNextLinefromTrainingData()) != null) {
			
			String appendedLine = line + "," + valueToAppend + "\n";
			
			try {
				bufferedWriterForAppending.write(appendedLine);
				//bufferedWriterForAppending.flush();
				timestepInAppendingFileTraining++;
				
			} catch (IOException e) {

				e.printStackTrace();
			}
			
		}
			
		return(line);
	}
	

	public String copyFileAppendColumnTesting(String valueToAppend) {
		
		String line;
		if ((line = getNextLinefromTestingData()) != null) {
			
			String appendedLine = line + "," + valueToAppend + "\n";
			
			try {
				bufferedWriterForAppending.write(appendedLine);
				timestepInAppendingFileTesting++;
				
			} catch (IOException e) {

				e.printStackTrace();
			}
			
		}
			
		return(line);
	}

	
	/* If bTraining is false, then this function using the Testing filename */
	private BufferedWriter readyForCopyAndAppending(boolean bTraining) {
		
		BufferedWriter bufferedWriter = null;
		
		/* Open the files of testing windspeed samples */
		/*
		 * The following method is derived from the one posted at
		 * http://crunchify.com/java-properties-file-how-to-read-config-properties-
		 * values-in-java/ public String getPropValues() throws IOException {
		 * 
		 */
		/* Create copy file of minute-by-minute windspeed samples */
		String[] tokens;
		if (bTraining) {

			tokens = TRAINING_WINDSPEED_FILENAME.split("\\.(?=[^\\.]+$)"); // adapted from answer posted on stackoverflow
			
		} else {
			
			tokens = TESTING_WINDSPEED_FILENAME.split("\\.(?=[^\\.]+$)"); // adapted from answer posted on stackoverflow
			
		}
		String FILENAME_BASE = tokens[0].substring(1); //omit leading backslash 
		String FILENAME_EXTENSION = tokens[1];
		
		String filename = FILENAME_BASE + "r." + FILENAME_EXTENSION;		

		File file;
		
		try {
			file = new File(filename);
			
			if (!file.exists()) {
				
				file.createNewFile();		
				
			}
			
			FileWriter fileWriter = new FileWriter(file);
			bufferedWriter = new BufferedWriter(fileWriter);
			
		} catch (IOException e) {
			e.printStackTrace();
		}


		return(bufferedWriter);		

		
	}
	
	public void writeTrainingFileUntilTimestep(String valueToAppend, int endingExclusiveTimestep) {
		
		while(timestepInAppendingFileTraining < endingExclusiveTimestep) {
		
			copyFileAppendColumnTraining(valueToAppend);
			
		}
		
	}

	public void writeTestingFileUntilTimestep(String valueToAppend, int endingExclusiveTimestep) {
		
		while(timestepInAppendingFileTesting < endingExclusiveTimestep) {
		
			copyFileAppendColumnTesting(valueToAppend);
			
		}
		
	}

	
	
	// TODO: complete closing of file
	public void writeFinalDeploymentStatusAndCloseAppendedFile(String valueToAppend,
																boolean bTraining) {
		
		
		if (bTraining) {
			
			while(copyFileAppendColumnTraining(valueToAppend) != null) {}
			
		} else {
			
			while(copyFileAppendColumnTesting(valueToAppend) != null) {}
			
		}
		
		// close input and output files
		// TODO closeTrainingFile();
		try {
			bufferedWriterForAppending.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	/************************************************************/
	/* Future: Code to allow for appending of future column     */
	/************************************************************/
	// readyForCopyAndAppending
	
	public void prepareToCopyAndAppendFuture(String headerToAppend,
			boolean bTraining, int minutesToLookAhead) {
		
		bufferedWriterForAppending = readyForCopyAndAppending(bTraining); // create destination file
		
		if (bTraining) {
			
			openTrainingFileFuture(minutesToLookAhead);
			
		} else {
			
			openTestingFileFuture(minutesToLookAhead); 
		}
			
		try {
			bufferedWriterForAppending.write(fileHeader + "," + headerToAppend + "\n");
		} catch (IOException e) {

			e.printStackTrace();
		}

	}
	
	
	public String copyFileAppendColumnTrainingFuture(String valueToAppend) {
		
		String line;
		if ((line = getNextLinefromTrainingDataFuture()) != null) {
			
			String appendedLine = line + "," + valueToAppend + "\n";
			
			try {
				bufferedWriterForAppending.write(appendedLine);
				timestepInAppendingFileTraining++;
				
			} catch (IOException e) {

				e.printStackTrace();
			}
			
		}
			
		return(line);
	}

	public String copyFileAppendColumnTestingFuture(String valueToAppend) {
		
		String line;
		if ((line = getNextLinefromTestingDataFuture()) != null) {
			
			String appendedLine = line + "," + valueToAppend + "\n";
			
			try {
				bufferedWriterForAppending.write(appendedLine);
				timestepInAppendingFileTesting++;
				
			} catch (IOException e) {

				e.printStackTrace();
			}
			
		}
			
		return(line);
	}
	
	
}