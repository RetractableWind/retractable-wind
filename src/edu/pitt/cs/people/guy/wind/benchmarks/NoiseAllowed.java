package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoiseAllowed {

	public final int STATION = 0;
	public final int MON_THU_DAYTIME_START = 1;
	public final int MON_THU_DAYTIME_END = 2;
	public final int FRI_DAYTIME_START = 3;
	public final int FRI_DAYTIME_END = 4;
	public final int SAT_DAYTIME_START = 5;
	public final int SAT_DAYTIME_END = 6;
	public final int SUN_DAYTIME_START = 7;
	public final int SUN_DAYTIME_END = 8;
	public final int FEDERAL_HOLIDAY_DAYTIME_START = 9;
	public final int FEDERAL_HOLIDAY_DAYTIME_END = 10;
	public final int TOTAL_NUMBER_OF_FIELDS = 11;  

	public LocalTime localTime[] = new LocalTime[TOTAL_NUMBER_OF_FIELDS];
	public boolean bFederalHolidaysObserved = true; 
	public boolean bUseSunsetEveryDayExcept = false; //Except if Daytime Start and End Times Equal 
	final int VALUE_INDICATING_LOCAL_TIME_AFTER_SUNSET_INDEX_HAS_NOT_BEEN_INITIALIZED = -99; //do not use -1
	int localTimeAfterSunsetIndex = VALUE_INDICATING_LOCAL_TIME_AFTER_SUNSET_INDEX_HAS_NOT_BEEN_INITIALIZED;
    final int INITIAL_VALUE_FOR_prevDayOfMonth = -1;
	int prevDayOfMonth = INITIAL_VALUE_FOR_prevDayOfMonth;
	
	public void reset() {
		
		localTimeAfterSunsetIndex = VALUE_INDICATING_LOCAL_TIME_AFTER_SUNSET_INDEX_HAS_NOT_BEEN_INITIALIZED;
		prevDayOfMonth = INITIAL_VALUE_FOR_prevDayOfMonth;
		
	}

	private LocalTime processField(String fieldValue) {


		final int DEFAULT_HOUR = 1;
		final int DEFAULT_MINUTE = 1;

		LocalTime lt = LocalTime.of(DEFAULT_HOUR, DEFAULT_MINUTE);

		final char FIRST_CHARACTER_OF_NEVER_STRING = 'N';
		final char FIRST_CHARACTER_OF_WITH_DAY_OF_WEEK_STRING = 'W';
		final char FIRST_CHARACTER_SUNSET_STRING = 's';



		switch(fieldValue.charAt(0)) {

		case FIRST_CHARACTER_OF_NEVER_STRING:

			// defer to default time, which is set above

			break;

		case FIRST_CHARACTER_OF_WITH_DAY_OF_WEEK_STRING:

			bFederalHolidaysObserved = false;

			break;

		case FIRST_CHARACTER_SUNSET_STRING:

			bUseSunsetEveryDayExcept = true;
			System.out.println("Using sunset for all noise-allowed days");
			break;

		default:

			// Parse hour and minute
			String timeParts[] = fieldValue.split(":");
			lt = LocalTime.of(Integer.parseInt(timeParts[0]),
					Integer.parseInt(timeParts[1]));


		}


		return(lt);


	}


	public NoiseAllowed(String station) {

		final String FILENAME = "/Noise_Allowed_Time_Definitions_All_Stations.csv";
		final String FILENAME_SUNSET_STL = "/STL_Sunset_Times.csv";

		// Load definition from .csv file

		/// Open .csv file
		BufferedReader br = openFile(FILENAME);

		String line = null;

		/// Find the station in the .csv file
		try {
			while (
					((line = br.readLine()) != null)
					&&
					!(line.substring(0, station.length()).equals(station))
					) {

				// do nothing

			}
		} catch (IOException e) {

			e.printStackTrace();
		}

		if (line == null) {

			System.out.println("NoiseAllowed record not found for station:" + station);

		} else {

			String tokens[] = line.split(",");
			// load record
			System.out.println("The station is the record is " + tokens[0]);
			for (int i =  MON_THU_DAYTIME_START; i < TOTAL_NUMBER_OF_FIELDS; i++) {

				// process field
				localTime[i] = processField(tokens[i]);


			}


		}


		if (station.equals("KSTL")) {			

			System.out.println("Reading sunset data for KSTL");
			BufferedReader brSunsetTimesSTL = openFile(FILENAME_SUNSET_STL);
			loadSunsetTimesIntoMemory(brSunsetTimesSTL);

		}


	}


	private boolean bDaytimeGivenStartAndEndConstants(int daytime_start_constant, int daytime_end_constant, LocalDateTime ldt) {
				
		boolean bDaytimeStartBeforeLocalTime = localTime[daytime_start_constant].isBefore(ldt.toLocalTime());
		

		localTimeThatDaytimeEndsPreliminary = ldt.toLocalTime();
		boolean bLocalTimeBeforeDaytimeEnd =  bUseSunsetEveryDayExcept ?
				bLocalTimeBeforeSunset(ldt) :
				ldt.toLocalTime().isBefore(localTime[daytime_end_constant]);
		
		return(
				!(localTime[daytime_start_constant].equals(localTime[daytime_end_constant])) && // if start and end equal, then return false		
				bDaytimeStartBeforeLocalTime &&
						bLocalTimeBeforeDaytimeEnd
						
						);
		
		
	}
	
	
	public double getMembershipValueForApproachingQuietHours(LocalDateTime ldt,
			double minutesAwayFromStartOfQuietHoursXIntercept) {
		
		final int MAXIMUM_MEMBERSHIP_VALUE = 1;
		final int LEAST_MEMBERSHIP_VALUE = 0;

		double membershipValue = MAXIMUM_MEMBERSHIP_VALUE; // default
		if (bIsNoiseAllowed(ldt)) {
			
			long minutesUntilEndOfDay =
					ldt.toLocalTime().until(localTimeThatDaytimeEndsPreliminary, ChronoUnit.MINUTES);
			if (minutesUntilEndOfDay < 0) {
				
				System.out.println("Warning: minutesUntilEndOfDay is unexpectedly negative");
				
			}
			membershipValue = (-MAXIMUM_MEMBERSHIP_VALUE/((double) minutesAwayFromStartOfQuietHoursXIntercept))
					*minutesUntilEndOfDay
					+ MAXIMUM_MEMBERSHIP_VALUE;
			
			if (membershipValue < LEAST_MEMBERSHIP_VALUE) {
				
				membershipValue = LEAST_MEMBERSHIP_VALUE;
				
			}
			
		} // else return default value set above
		
		return(membershipValue);
		
	}
	
	LocalTime localTimeThatDaytimeEndsPreliminary;
	public boolean bIsNoiseAllowed(LocalDateTime ldt) {


		if (
				bUseSunsetEveryDayExcept &&
				(VALUE_INDICATING_LOCAL_TIME_AFTER_SUNSET_INDEX_HAS_NOT_BEEN_INITIALIZED == localTimeAfterSunsetIndex)

				) {
			
				localTimeAfterSunsetIndex = getIndexOfLocalDateTimeSunsetStandardTime(ldt) - 1; //Subtract 1 since the index is advanced before it is used
																																								  // if there is a day change.
						
		}
		
		// TODO: ensure that ldt observes Daylight Savings Time appropriately
		boolean bNoiseAllowed = false;
		
		// if sunset times are used, then adjust DAYTIME end
		
		

		if (bFederalHolidaysObserved && FederalHolidays.bFederalHoliday(ldt.toLocalDate())) {

			
			bNoiseAllowed = bDaytimeGivenStartAndEndConstants(FEDERAL_HOLIDAY_DAYTIME_START, 
					FEDERAL_HOLIDAY_DAYTIME_END, ldt);
			

		} else {



			switch(ldt.getDayOfWeek()) {

			case MONDAY:
			case TUESDAY:
			case WEDNESDAY:
			case THURSDAY:

				bNoiseAllowed = bDaytimeGivenStartAndEndConstants(MON_THU_DAYTIME_START, 
						MON_THU_DAYTIME_END, ldt);
				

				break;

			case FRIDAY:


				bNoiseAllowed = bDaytimeGivenStartAndEndConstants(FRI_DAYTIME_START, 
						FRI_DAYTIME_END, ldt);
				

				break;


			case SATURDAY:


				bNoiseAllowed = bDaytimeGivenStartAndEndConstants(SAT_DAYTIME_START, 
						SAT_DAYTIME_END, ldt);				
				
				break;

			case SUNDAY:


				bNoiseAllowed = bDaytimeGivenStartAndEndConstants(SUN_DAYTIME_START, 
						SUN_DAYTIME_END, ldt);				


				break;



			}

		}		

		return(bNoiseAllowed);

	}



	boolean bLocalTimeBeforeSunset(LocalDateTime ldt) {
 

		//if new day, then advance index
		if (prevDayOfMonth != ldt.getDayOfMonth()) {

			localTimeAfterSunsetIndex++;
			prevDayOfMonth = ldt.getDayOfMonth();
		}
		if (localTimeAfterSunsetIndex<0) {
			
			System.out.println("Warning: localTimeAfterSunsetIndex unexpectedly negative; setting to 0.");
			localTimeAfterSunsetIndex=0;
			
		}
		//assume that localTimeAfterSunsetIndex has been set correctly
		localTimeThatDaytimeEndsPreliminary = localDateTimeSunsetStandardTime[localTimeAfterSunsetIndex].toLocalTime();
		boolean bLocalTimeBeforeSunset = ldt.toLocalTime().isBefore(localTimeThatDaytimeEndsPreliminary);
		
		// check that date matches
		if (
				!(ldt.toLocalDate().equals(localDateTimeSunsetStandardTime[localTimeAfterSunsetIndex].toLocalDate()))
				
				) {
			
//			System.out.println("Error: LocalTimeBeforeSunset: Dates out-of-sync: passed" +
//			ldt.toLocalDate() + " Indexed: " + 
//			localDateTimeSunsetStandardTime[localTimeAfterSunsetIndex].toLocalDate());

			System.out.println("Error: LocalTimeBeforeSunset: Dates out-of-sync: passed" +
			ldt + " Indexed: " + 
			localDateTimeSunsetStandardTime[localTimeAfterSunsetIndex]);
			
			System.exit(0);
			
		}
			
			
		
		return(bLocalTimeBeforeSunset);
		
	}
	

	final int NUMBER_DAYS_IN_2004_2014 = 4018;
	LocalDateTime  localDateTimeSunsetStandardTime[] = new LocalDateTime[NUMBER_DAYS_IN_2004_2014];
	
	private void loadSunsetTimesIntoMemory(BufferedReader brSunsetTimes) {

		String line;
		try {

			int i = 0;
			while((line = brSunsetTimes.readLine()) != null) {

				String tokens[] = line.split(",");
				String date = tokens[0];
				String sunsetTime = tokens[1];

				String dateTokens[] = date.split("-");
				int year = Integer.parseInt(dateTokens[0]);
				int month = Integer.parseInt(dateTokens[1]);
				int dayOfMonth = Integer.parseInt(dateTokens[2]);

				int hour = Integer.parseInt(sunsetTime.substring(0, 2));
				int minute = Integer.parseInt(sunsetTime.substring(2));

				localDateTimeSunsetStandardTime[i] = LocalDateTime.of(year, month, dayOfMonth, hour, minute);
				i++;



			}



		} catch (IOException e) {

			e.printStackTrace();


		}



	}


	public LocalTime localTimeSunset(int index, boolean bAdjustOutputForDaylightSavingsTime) {

		int hour = localDateTimeSunsetStandardTime[index].getHour();
		int minute = localDateTimeSunsetStandardTime[index].getMinute();

		if (bAdjustOutputForDaylightSavingsTime) {

			hour++;

		}

		return(LocalTime.of(hour, minute));


	}


	int getIndexOfLocalDateTimeSunsetStandardTime(LocalDateTime ldt) {

		// search forward until the proper date is found
		int i = 0;
		int yearToMatch = ldt.getYear();
		int monthValueToMatch = ldt.getMonthValue();
		int dayToMatch = ldt.getDayOfMonth();

		for (i = 0; i < localDateTimeSunsetStandardTime.length; i++) {

			if ((yearToMatch == localDateTimeSunsetStandardTime[i].getYear()) &&
					(monthValueToMatch == localDateTimeSunsetStandardTime[i].getMonthValue()) &&
					(dayToMatch == localDateTimeSunsetStandardTime[i].getDayOfMonth())) {

				break;

			};


		}

		if (i == localDateTimeSunsetStandardTime.length) {

			System.out.println("Error: getIndexOfLocalDateTimeSunsetStandardTime: Date not found " + ldt);
			System.exit(0);

		}

		return(i);

	}



	public void getFields() {


		for (int i =  MON_THU_DAYTIME_START; i < TOTAL_NUMBER_OF_FIELDS; i++) {

			// process field
			System.out.println(i + " " + localTime[i]);


		}
		System.out.println("Federal Holidays Observed:" + bFederalHolidaysObserved);


	}

	public void getSunsetTimes() {


		for (LocalDateTime ldt : localDateTimeSunsetStandardTime) {

			System.out.println("ldt:"+ldt);

		}


	}


	public BufferedReader openFile(String filename) {

		BufferedReader bufferedReader = null;

		try {

			InputStream inputStream = this.getClass().getResourceAsStream(filename);

			if (inputStream == null) {
				System.out.println("Null for " + filename);

			} else {

				System.out.println("Success: " + filename + " found.");

			}
			 bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

			if (bufferedReader != null) {

				// advance past header
				if ((bufferedReader.readLine()) != null) {
					// //System.out.println("The header of the training file is " + line);
				}

			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return (bufferedReader);

	}


	public static class FederalHolidays {

		public static boolean bFederalHoliday(LocalDate date) {

			boolean bIsHoliday = false;

			switch (date.getMonth()) {

			case JANUARY:

				switch (date.getDayOfMonth()) {

				case 1: // New Year's Day

					bIsHoliday = true;

					break;

				default:

					// MLK, Jr.
					// Determine whether incoming date is the third Monday in January
					if ((date.getDayOfWeek() == DayOfWeek.MONDAY) && (date.getDayOfMonth() >= 15)
							&& (date.getDayOfMonth() <= 21)) {

						bIsHoliday = true;

					}

					break;


				}

				break; // break January

			case MAY:

				// Determine whether last Monday in May
				if ((date.getDayOfWeek() == DayOfWeek.MONDAY) && (date.getDayOfMonth() >= 25)
						&& (date.getDayOfMonth() <= 31)) {

					bIsHoliday = true;

				}

				break; // break May

			case JULY:

				if (date.getDayOfMonth() == 4) {

					bIsHoliday = true;

				}

				break; // break July


			case SEPTEMBER:

				// Determine whether first Monday in September
				if ((date.getDayOfWeek() == DayOfWeek.MONDAY) && (date.getDayOfMonth() >= 1)
						&& (date.getDayOfMonth() <= 7)) {

					bIsHoliday = true;

				}

				break; // break September

			case NOVEMBER:

				// Determine whether forth Thursday in November
				if ((date.getDayOfWeek() == DayOfWeek.THURSDAY) && (date.getDayOfMonth() >= 22)
						&& (date.getDayOfMonth() <= 28)) {

					bIsHoliday = true;					

				}

				break; // break November

			case DECEMBER:

				if (date.getDayOfMonth() == 25) {

					bIsHoliday = true;

				}

				break; // break December

			default:

				break;
			}	


			return (bIsHoliday);

		}

	}

}

