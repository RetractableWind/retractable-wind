package edu.pitt.cs.people.guy.wind.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class PermanentlyDeployed {

 HarvesterModel harvester; 
 Workload ws;
 String station;
 ElectricityPrice ep;
 
	public PermanentlyDeployed(String localStation, HarvesterModel localHarvester, Workload localWorkload) {

		harvester = localHarvester;

		ws = localWorkload;
	
		station = localStation;
		
		ep = new ElectricityPrice(ws.HOURLY_ENERGY_PRICE_FILENAME);
		
}
	
	public double findEnergyHarvestedKwhTraining() {
				
		Workload.WindspeedSample  sample;
		
		//harvester.resetAll();
		
		harvester.resetAll(ep);
		
		while ((sample = ws.getNextWindspeedSampleTraining()) != null) {

			harvester.processMode(sample, ep, false);
			
		}		

		return(harvester.getEnergyHarvestedAVAILABLEKilowattHours());
		
	}	
	

	
	public double findEnergyHarvestedKwhTesting() {
		
		Workload.WindspeedSample  sample;
		
		harvester.resetAll(ep);
		
		while ((sample = ws.getNextWindspeedSampleTesting()) != null) {

			
			harvester.processMode(sample, ep, false);
			
		}		

		return(harvester.getEnergyHarvestedAVAILABLEKilowattHours());
		
	}	
	
	
}