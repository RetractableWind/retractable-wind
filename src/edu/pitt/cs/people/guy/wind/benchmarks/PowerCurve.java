package edu.pitt.cs.people.guy.wind.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.io.InputStreamReader;

public class PowerCurve {

	public final double v90_AREA_SQM = 6362;
	public final String POWER_CURVE_FILE = "/Wind turbine power curves - wind turbine power output against wind speed in knots.csv";
	public final int V90_COLUMN = 6;

	private List<Integer> powerList = new ArrayList<Integer>();
	private List<Double> scaledList = new ArrayList<Double>();

	/**
	 * Constructor
	 * 
	 * @param iHarvesterSizeSQM
	 *            The area of wind the harvester intercepts in square meters
	 */
	public PowerCurve(int iHarvesterSizeSQM) {

		System.out.println("Reading file");

		String line = null;

		try {
			InputStream inPowerCurve = this.getClass().getResourceAsStream(POWER_CURVE_FILE);
			if (inPowerCurve == null) {
				System.out.println("Null for " + POWER_CURVE_FILE);
				System.exit(0); // end the program

			} else {

				System.out.println("Success: " + POWER_CURVE_FILE + " found.");

			}
			BufferedReader br = new BufferedReader(new InputStreamReader(inPowerCurve));
			line = br.readLine(); // read table header
			String[] cols = line.split(",");
			System.out.println("Speed (Knots) = " + cols[0] + " , Power (kW) =" + cols[V90_COLUMN]);
			// read values
			while ((line = br.readLine()) != null) {
				// use comma as separator
				cols = line.split(",");
				System.out.println("Speed (Knots) = " + cols[0] + " , Power (kW) =" + cols[V90_COLUMN]);
				powerList.add(Integer.parseInt(cols[V90_COLUMN]));
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		scaleList(iHarvesterSizeSQM);

	}

	private void scaleList(int iHarvesterSizeSQM) {

		double scale = iHarvesterSizeSQM / v90_AREA_SQM;
		System.out.println("The scale is " + scale);
		for (int i = 0; i < powerList.size(); i++) {
			scaledList.add(powerList.get(i) * scale);
		}
	}

	public double windPowerKilowattsViaTable(int windSpeedKnots, boolean bInCutOutRangeLocal) {

		if (windSpeedKnots < scaledList.size() && !bInCutOutRangeLocal) {
			return (scaledList.get(windSpeedKnots));
		} else {
			return (0);
		}
	}
	/*
	 * public double windPowerWattsViaTable(int windSpeedKnots) { if (windSpeedKnots
	 * < scaledList.size()) { return(scaledList.get(windSpeedKnots)*1000); } else {
	 * System.out.println("Warning: windSpeedKnots=" + windSpeedKnots +
	 * " beyond table."); return(0); } }
	 */

}
