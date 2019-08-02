package edu.pitt.cs.people.guy.wind.benchmarks;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
/* */
/* Deploy or retract must be called every minute */
class AppendFutureValuesToWorkload3 {

	HarvesterModel harvester;
	Workload ws;
	
	private double findMeanOfWindspeedsInElevenYearFile(String localStation) {
		
		
		String filename ="/training" +  localStation + "2004-2014abcd.csv";
		//String filename = "/trainingKPITminimal_file.csv";
		final int WINDSPEED_COLUMN = 1; 
		
		// open file
		BufferedReader bufferedReader = ws.openWindspeedFile(filename);
		
		// read entire file
		String line;
		Double windspeedTotalKnots = 0.0;
		long i = 0;
		
	try {
		
		while ((line = bufferedReader.readLine()) != null) {
			
			String tokens[] = line.split(",");
			windspeedTotalKnots += Integer.parseInt(tokens[WINDSPEED_COLUMN]);
			i++;
		}
		
	} catch (IOException e) {
		
		e.printStackTrace();
		
	}
		// close file
	
	try {
		bufferedReader.close();

	} catch (IOException e) {
		
		e.printStackTrace();
				
	}
		
		// calculate mean
		Double mean =  windspeedTotalKnots/i;
		
		return(mean);
		
	}
	
	public AppendFutureValuesToWorkload3(String localStation,
											HarvesterModel localHarvester,
											Workload localWorkload,
											int minutesToLookAhead,
											boolean bTraining) {

		harvester = localHarvester;
		ws = localWorkload;
				
		ws.openTrainingFileFuture(minutesToLookAhead);
		
		//ws.openTestingFileFuture(minutesToLookAhead);


		Workload.WindspeedSample sample;
		
		 double windspeedMeanKnots = findMeanOfWindspeedsInElevenYearFile(localStation);
		
		System.out.println(localStation + ":  The mean is " + windspeedMeanKnots);
				
		
		ws.prepareToCopyAndAppend("f" + minutesToLookAhead, bTraining);
		
		if (bTraining) {
			
			
			while ((sample = ws.getNextWindspeedSampleTrainingPredicted(windspeedMeanKnots)) != null) {
				
				ws.copyFileAppendColumnTraining("" + sample.windspeed_knots);
				

			}			
			
		} else {
			
			
			while ((sample = ws.getNextWindspeedSampleTestingPredicted(windspeedMeanKnots)) != null) {
				
				ws.copyFileAppendColumnTesting("" + sample.windspeed_knots);
			
			
		}
			
	}
		
   // Write -1 where no future wind speed is available	
	ws.writeFinalDeploymentStatusAndCloseAppendedFile("-1", bTraining);
	
	
	}
	
	
	
}