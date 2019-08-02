package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;




import java.util.ArrayList;

public class ElectricityPrice {

	private BufferedReader br;
	//private BufferedWriter bw;
	
	private ArrayList<Double> listElectricityHarvestedCanadianDollars = new ArrayList<Double>();

	private int iGeneratingEnergyWhenGridNeedsEnergyMinutes = 0;
	private int iUsingEnergyWhenGridNeedsEnergyMinutes = 0;

	private int iGeneratingEnergyWhenGridHasExcessEnergyMinutes = 0;
	private int iUsingEnergyWhenGridHasExcessEnergyMinutes = 0;

	private int iIdleWhenGridDoesNotNeedEnergyMinutes = 0;
	private int iIdleWhenGridNeedsEnergyMinutes = 0;
	
	private int iEnergyPriceIsZeroMinutes = 0;

	// This-month version
	private int iGeneratingEnergyWhenGridNeedsEnergyMinutesThisMonth = 0;
	private int iUsingEnergyWhenGridNeedsEnergyMinutesThisMonth = 0;

	private int iGeneratingEnergyWhenGridHasExcessEnergyMinutesThisMonth = 0;
	private int iUsingEnergyWhenGridHasExcessEnergyMinutesThisMonth = 0;

	private int iIdleWhenGridDoesNotNeedEnergyMinutesThisMonth = 0;
	private int iIdleWhenGridNeedsEnergyMinutesThisMonth = 0;
	
	private int iEnergyPriceIsZeroMinutesThisMonth = 0;
	
	private double dMarketBalancePercentageThisMonthPrevious = Double.MIN_VALUE;
	
	
	
	public void resetAll() {
		
		resetCounters();
		resetListElectricityHarvestedCanadianDollars();
		
		epfc = new ElectricityPriceFileCursor();
		
		if (br != null) {
			
				try {
					br.close();
					br = openFile(electricityPriceFileName);
				} catch (IOException e) {
					e.printStackTrace();
				}
	
		}
		
	}
	public void resetCounters() {
		
		iGeneratingEnergyWhenGridNeedsEnergyMinutes = 0;
		iUsingEnergyWhenGridNeedsEnergyMinutes = 0;

		iGeneratingEnergyWhenGridHasExcessEnergyMinutes = 0;
		iUsingEnergyWhenGridHasExcessEnergyMinutes = 0;

		iIdleWhenGridDoesNotNeedEnergyMinutes = 0;
		iIdleWhenGridNeedsEnergyMinutes = 0;
		
		iEnergyPriceIsZeroMinutes = 0;
		
		resetCountersMonthly();
		
	}

	public void resetCountersMonthly() {
		
		dMarketBalancePercentageThisMonthPrevious = 
				getMarketBalancePercentageThisMonth();
		
		iGeneratingEnergyWhenGridNeedsEnergyMinutesThisMonth = 0;
		iUsingEnergyWhenGridNeedsEnergyMinutesThisMonth = 0;

		iGeneratingEnergyWhenGridHasExcessEnergyMinutesThisMonth = 0;
		iUsingEnergyWhenGridHasExcessEnergyMinutesThisMonth = 0;

		iIdleWhenGridDoesNotNeedEnergyMinutesThisMonth = 0;
		iIdleWhenGridNeedsEnergyMinutesThisMonth = 0;
		
		iEnergyPriceIsZeroMinutesThisMonth = 0;
		
	}
	
	public void printStatisticsOfGridMatching() {

		System.out.println("iGeneratingEnergyWhenGridNeedsEnergyMinutes =" +
				iGeneratingEnergyWhenGridNeedsEnergyMinutes);

		System.out.println("iGeneratingEnergyWhenGridNeedsEnergyMinutes =" +
				iGeneratingEnergyWhenGridNeedsEnergyMinutes);

		System.out.println("iUsingEnergyWhenGridNeedsEnergyMinutes =" +
				iUsingEnergyWhenGridNeedsEnergyMinutes);

		System.out.println("iGeneratingEnergyWhenGridHasExcessEnergyMinutes = " +
				iGeneratingEnergyWhenGridHasExcessEnergyMinutes);

		System.out.println("iUsingEnergyWhenGridHasExcessEnergyMinutes = " +
				iUsingEnergyWhenGridHasExcessEnergyMinutes);

		System.out.println("iIdleWhenGridDoesNotNeedEnergyMinutes = " +
				iIdleWhenGridDoesNotNeedEnergyMinutes);

		System.out.println("iIdleWhenGridNeedsEnergyMinutes = " +
				iIdleWhenGridNeedsEnergyMinutes);
		
		System.out.println("iEnergyPriceIsZeroMinutes = " +
				iEnergyPriceIsZeroMinutes);
		
		int iHelpingMinutes = iGeneratingEnergyWhenGridNeedsEnergyMinutes + 
				iUsingEnergyWhenGridHasExcessEnergyMinutes;
		int iHurtingMinutes = iUsingEnergyWhenGridNeedsEnergyMinutes +
				iGeneratingEnergyWhenGridHasExcessEnergyMinutes;
		


	}

	double getMarketBalancePercentageThisMonthPrevious() {
		
		return(dMarketBalancePercentageThisMonthPrevious);
		
	}


	
	
	double getMarketBalancePercentageThisMonth() {
		
		int iHelpingMinutes = iGeneratingEnergyWhenGridNeedsEnergyMinutesThisMonth + 
				iUsingEnergyWhenGridHasExcessEnergyMinutesThisMonth;
		int iHurtingMinutes = iUsingEnergyWhenGridNeedsEnergyMinutesThisMonth +
				iGeneratingEnergyWhenGridHasExcessEnergyMinutesThisMonth;
		
		double dBalancePercentage = (iHelpingMinutes - iHurtingMinutes) /
															  ((double) (iHelpingMinutes + 1));  // Add 1 to avoid dividing by zero

		return(dBalancePercentage);
		
	}

	
	
	double getMarketBalancePercentage() {
		
		int iHelpingMinutes = iGeneratingEnergyWhenGridNeedsEnergyMinutes + 
				iUsingEnergyWhenGridHasExcessEnergyMinutes;
		int iHurtingMinutes = iUsingEnergyWhenGridNeedsEnergyMinutes +
				iGeneratingEnergyWhenGridHasExcessEnergyMinutes;
		
		double dBalancePercentage = (iHelpingMinutes - iHurtingMinutes) /
															  ((double) (iHelpingMinutes +1)); // add 1 to avoid dividing by 0

		return(dBalancePercentage);
		
	}

	public BufferedReader openFile(String filename) {

		BufferedReader bufferedReader = null;

		/*
		 * The following method is derived from the one posted at
		 * http://crunchify.com/java-properties-file-how-to-read-config-properties-
		 * values-in-java/ public String getPropValues() throws IOException {
		 * 
		 * 
		 */

		try {

			InputStream inputStream = this.getClass().getResourceAsStream(filename);

			if (inputStream == null) {
				System.out.println("Null for " + filename);

			} 
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			if (bufferedReader != null) {
				// advance past first line of header
				if ((bufferedReader.readLine()) != null) {

				}
				// advance past second line of header
				if ((bufferedReader.readLine()) != null) {	
					
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return(bufferedReader);		

	}

	class ElectricityPriceFileCursor{

		private String tokens[] = {"-1", "-1", "-1"}; //initialize to hour that will not be in price file 
		LocalDate fileLocalDate = LocalDate.of(0, 1, 1); //initialize to date that will not be in price file					
		
		private double advancetoLocalDateTimePricePerKWH(LocalDateTime ldt) {
			
			final int DATE_COLUMN = 0;
			final int HOUR_0_TO_23_COLUMN = 1;
			final int ENERGY_PRICE_PER_KWH_COLUMN = 2;

			//DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-LLL-yy");
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMM-yy");


			try {

				String line;

				//System.out.println("Passed-in date is " + ldt);

				while (!(
						fileLocalDate.isEqual(ldt.toLocalDate())
						&&
						Integer.parseInt(tokens[HOUR_0_TO_23_COLUMN]) == ldt.getHour()			
						)) {

					line = br.readLine();
					//System.out.println(line);
					tokens = line.split(","); 


					if (tokens.length != 3) {

						System.exit(0);

					}


					fileLocalDate = LocalDate.parse(tokens[DATE_COLUMN], formatter);
					//System.out.println(fileLocalDate);


				} 

			} catch (IOException e) {
				e.printStackTrace();
			}

			return(Double.parseDouble(tokens[ENERGY_PRICE_PER_KWH_COLUMN]));

		}	


	}

	ElectricityPriceFileCursor epfc;
	String electricityPriceFileName = "";

	ElectricityPrice(String filename) {

		// open price file
		electricityPriceFileName = filename;
		br = openFile(filename);		
		epfc = new ElectricityPriceFileCursor();
		
//		// create dataset for four-quadrant scatter plot
//		final String DATASET_FOR_QUADRANT_FILENAME = 
//				"Immediate_Market_Quadrant_Dataset.csv";
//		bw = prepareToWriteFile(DATASET_FOR_QUADRANT_FILENAME);
//
//		if (bw == null) {
//			
//			System.out.println("Error opening output file " + DATASET_FOR_QUADRANT_FILENAME);
//			System.exit(0);
//			
//		} else {
//			
//			try {
//				bw.write("Price (CAD/kWm), EnergyHarvested (kWm)\n");
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//			
//		}
		
	}

	public void resetListElectricityHarvestedCanadianDollars() {

		listElectricityHarvestedCanadianDollars.clear();

	}

	class EnergyPriceCanadianDollarsVsTotalEnergyHarvestedKilowattMinuteChange {

		double  dEnergyPriceCanadianDollars;
		double  dTotalEnergyHarvestedKilowattMinuteChange;

		EnergyPriceCanadianDollarsVsTotalEnergyHarvestedKilowattMinuteChange(
				double  dLocalEnergyPriceCanadianDollars,
				double  dLocalTotalEnergyHarvestedKilowattMinuteChange){

			dEnergyPriceCanadianDollars =dLocalEnergyPriceCanadianDollars;
			dTotalEnergyHarvestedKilowattMinuteChange =
					dLocalTotalEnergyHarvestedKilowattMinuteChange;

		}
	
	}

	// call every minute of the simulation
		public double calculateAndStoreEnergyHarvestedCanadianDollars(LocalDateTime ldt, 
				double dTotalEnergyHarvestedKilowattMinuteChange) {

			double dEnergyPriceCanadianDollarsPerKWMinute =
					epfc.advancetoLocalDateTimePricePerKWH(ldt)/60.0;
			
			updateMarketQuadrantScores(
					dEnergyPriceCanadianDollarsPerKWMinute,
					dTotalEnergyHarvestedKilowattMinuteChange);

//			storeEnergyPriceCanadianDollarsVsTotalEnergyHarvestedKilowattMinuteChangeDataPoint(
//					dEnergyPriceCanadianDollarsPerKWMinute,
//					dTotalEnergyHarvestedKilowattMinuteChange);
			
			double dEnergyHarvestedCanadianDollars = 
					dEnergyPriceCanadianDollarsPerKWMinute*dTotalEnergyHarvestedKilowattMinuteChange;


			listElectricityHarvestedCanadianDollars.add(dEnergyHarvestedCanadianDollars);

			return(dEnergyHarvestedCanadianDollars);

		}

		// call every minute
		void updateMarketQuadrantScores(
				double dEnergyPriceCanadianDollarsKilowattMinute,
				double dTotalEnergyHarvestedKilowattMinuteChange) {
			
			// Count data points in each quadrant
			if (dEnergyPriceCanadianDollarsKilowattMinute < 0) {

				if (dTotalEnergyHarvestedKilowattMinuteChange > 0) {

					iGeneratingEnergyWhenGridHasExcessEnergyMinutes++;					

				} else if (dTotalEnergyHarvestedKilowattMinuteChange < 0) {

					iUsingEnergyWhenGridHasExcessEnergyMinutes++;

				} else {

					iIdleWhenGridDoesNotNeedEnergyMinutes++;

				}

			} else if (dEnergyPriceCanadianDollarsKilowattMinute > 0) {

				if (dTotalEnergyHarvestedKilowattMinuteChange > 0) {

					iGeneratingEnergyWhenGridNeedsEnergyMinutes++;
					iGeneratingEnergyWhenGridNeedsEnergyMinutesThisMonth++;
					

				} else if (dTotalEnergyHarvestedKilowattMinuteChange < 0) {

					iUsingEnergyWhenGridNeedsEnergyMinutes++;
					iUsingEnergyWhenGridNeedsEnergyMinutesThisMonth++;
					
				} else {

					iIdleWhenGridNeedsEnergyMinutes++;
					iIdleWhenGridNeedsEnergyMinutesThisMonth++;

				}

			} else {
							
				iEnergyPriceIsZeroMinutes++;
				iEnergyPriceIsZeroMinutesThisMonth++;
				
			}
				
			
		}
		
		
//		void storeEnergyPriceCanadianDollarsVsTotalEnergyHarvestedKilowattMinuteChangeDataPoint(
//				double dEnergyPriceCanadianDollarsKilowattMinute,
//				double dTotalEnergyHarvestedKilowattMinuteChange) {
//					
//			try {
//				bw.write(dEnergyPriceCanadianDollarsKilowattMinute + "," +
//						dTotalEnergyHarvestedKilowattMinuteChange+"\n");
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//			
//
//		}

		public int calculateNetEC100(boolean bPrintFrequencyTable) {

			// find maximum
			double dElectricityHarvestedCanadianDollarsMaximum = Double.MIN_VALUE;
			double dElectricityHarvestedCanadianDollarsMinimum = Double.MAX_VALUE;

			// find range
			for (double dElectricityHarvestedCanadianDollars : listElectricityHarvestedCanadianDollars) {

				if (dElectricityHarvestedCanadianDollars < dElectricityHarvestedCanadianDollarsMinimum) {

					dElectricityHarvestedCanadianDollarsMinimum = dElectricityHarvestedCanadianDollars;

				}			
				if (dElectricityHarvestedCanadianDollars > dElectricityHarvestedCanadianDollarsMaximum) {

					dElectricityHarvestedCanadianDollarsMaximum = dElectricityHarvestedCanadianDollars;

				}

			}
			double dRange = dElectricityHarvestedCanadianDollarsMaximum -
					dElectricityHarvestedCanadianDollarsMinimum;

			// divide into buckets
			final int NUMBER_OF_BUCKETS = 100;
			int arrayNetECHistogram[] = new int[NUMBER_OF_BUCKETS];

			double dBucketSize = dRange / (NUMBER_OF_BUCKETS - 1);


			int highestBucketNumberOfBucketHoldingNegativeValues = NUMBER_OF_BUCKETS; 
			// "place" each profit(cost) into histogram
			for (double dElectricityHarvestedCanadianDollars : listElectricityHarvestedCanadianDollars) {

				int iBucketNumber = (int) Math.floor((dElectricityHarvestedCanadianDollars - 
						dElectricityHarvestedCanadianDollarsMinimum)/dBucketSize);

				if (dElectricityHarvestedCanadianDollars < 0) {

					//System.out.println("Negative " + dElectricityHarvestedCanadianDollars);

				}

				if ((dElectricityHarvestedCanadianDollars < 0) && 
						(highestBucketNumberOfBucketHoldingNegativeValues < iBucketNumber)) {

					highestBucketNumberOfBucketHoldingNegativeValues = iBucketNumber;

				}
				if (iBucketNumber == 100) {

					System.out.println("iBucketNumber too high");

				}
				arrayNetECHistogram[iBucketNumber]++;

			}

			// calculate NetEC(100)
			int sumOfBucketCountsOfBucketsHoldingNegativeValues = 0;
			if (highestBucketNumberOfBucketHoldingNegativeValues != NUMBER_OF_BUCKETS) { 
				for (int i = highestBucketNumberOfBucketHoldingNegativeValues; i>-1; i--) {

					sumOfBucketCountsOfBucketsHoldingNegativeValues += arrayNetECHistogram[i];

				}
			}

			if(bPrintFrequencyTable) {

				System.out.println("Bin Number" + "," +
						"Lower end of class interval" + "," +
						"Frequency");


				for (int i = 0; i<NUMBER_OF_BUCKETS; i++) {

					System.out.println(i + "," +
							(dElectricityHarvestedCanadianDollarsMinimum+i*dBucketSize) + "," +
							arrayNetECHistogram[i]);

				}



			}

			return(sumOfBucketCountsOfBucketsHoldingNegativeValues);

		}





		
		public BufferedWriter prepareToWriteFile(String filename) {

			File file;
			
			BufferedWriter bufferedWriter = null;
			
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
		
		public void closeFiles() {
			
			try {
				//bw.close();
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			
		}

	}