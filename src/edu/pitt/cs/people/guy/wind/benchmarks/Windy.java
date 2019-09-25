
package edu.pitt.cs.people.guy.wind.benchmarks;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;

import edu.pitt.cs.people.guy.wind.benchmarks.HarvesterModel;
import edu.pitt.cs.people.guy.wind.benchmarks.Workload;
import edu.pitt.cs.people.guy.wind.benchmarks.Windy;


public class Windy {

		final float LAMDA;	// If the membership value of a windspeed is equal or greater than
														// than Lamda, then the windspeed is considered to be Not Windy

	int LOWEST_WINDSPEED_THAT_IS_WINDY_KNOTS;
	
	int DEPLOYMENT_THRESHOLD;
	int RETRACTION_THRESHOLD;

	//UPGRADE: move these constants to a common place
	final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_PREFIX = "/training";
//	final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_POSTFIX = "2004-2008inMembershipFunctionNotWindy.out";
	final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_POSTFIX = "2004-2012inMembershipFunctionNotWindy.out";

	HarvesterModel harvester;

	RunningAverage running_average = null;

	Workload ws = null;
	
	ElectricityPrice ep;
	
	private List<Float> membershipFunctionForNotWindy = new ArrayList<Float>();

//	public Windy(String localStation, HarvesterModel localHarvester, Workload localWorkload) {
	
    public Windy(String localStation, float lambda) {


	LAMDA = lambda;

		//final String dir = System.getProperty("user.dir");
		//System.out.println("current dir = " + dir);

		final String MEMBERSHIP_FUNCTION_NOT_WINDY_FILE = MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_PREFIX
				+ localStation + MEMBERSHIP_FUNCTION_NOT_WINDY_FILE_PATTERN_POSTFIX;

		// harvester = localHarvester;

		// ws = localWorkload;
		
		/* Get membership function for NOT WINDY */
		InputStream inputStream = this.getClass().getResourceAsStream(MEMBERSHIP_FUNCTION_NOT_WINDY_FILE);

		if ( inputStream != null) {

			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
				System.out.println("I have found the membership file for NOT WINDY.");
				String line = br.readLine(); // read table header
				// read values
				int i = 0;
				while ((line = br.readLine()) != null) {
					// use comma as separator
					String[] cols = line.split("\\s+");
					float membershipValue = Float.parseFloat(cols[1]);
					membershipFunctionForNotWindy.add(membershipValue);
					System.out.println("Windspeed (knots) " + cols[0] + ", Membership Value =" + cols[1] + "," +
							membershipFunctionForNotWindy.get(i++));
				}
				br.close();
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

		LOWEST_WINDSPEED_THAT_IS_WINDY_KNOTS = findWindyThresholdKnots();
		
	}
	
	private int findWindyThresholdKnots() {
		
		/* In this case, the lambda-cut set is the set of all windspeeds in the fuzzy set
		 *  NOT WINDY having membership values of lambda or higher. 
		 *   
		 */
		

		int iWindspeed = 0;
		// find the lowest windspeed having the highest membership value less than lambda
		while (LAMDA < membershipFunctionForNotWindy.get(iWindspeed)) {
			
			iWindspeed++;
			
		}
		
		return(iWindspeed);
		
	}
	
	public int getLowestWindspeedThatIsWindyKnots() {
		
		return(LOWEST_WINDSPEED_THAT_IS_WINDY_KNOTS);
		
	}
	
	public boolean bIsWindy(int windspeed) {
	
		return(LOWEST_WINDSPEED_THAT_IS_WINDY_KNOTS <= windspeed);
		
	}

	
	
	
	public float getMembershipValueForWindyNot(int iWindspeed) {
				
		if (iWindspeed >= membershipFunctionForNotWindy.size()) {
			
			iWindspeed = membershipFunctionForNotWindy.size() - 1;
			
		} else if (iWindspeed < 0) {
			
			iWindspeed = 0;
			
		}
		
		return(membershipFunctionForNotWindy.get(iWindspeed));
		
	}
	
	public float getMembershipValueForWindy(int iWindspeed) {
		
		return(1-getMembershipValueForWindyNot(iWindspeed));
		
	}
	
	public float getMembershipValueForNotWindyVery(int iWindspeed) {
		
		final int  OFFSET = 2; 
		
		int iAdjustedWindspeed = iWindspeed + OFFSET;
		
		
		return(getMembershipValueForWindyNot(iAdjustedWindspeed));
		
	}

	public float getMembershipValueForVeryWindy(int iWindspeed) {
		
		return(1-getMembershipValueForNotWindyVery(iWindspeed));
		
	}
	
	}
	



	
	
	
	
	
