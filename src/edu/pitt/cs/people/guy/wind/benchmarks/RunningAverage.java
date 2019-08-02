package edu.pitt.cs.people.guy.wind.benchmarks;

	
	class RunningAverage {
		
		private int[] past_readings = null;
		private int sum = 0;
		private int previousWindowSize = -1;
		private int tail;
		private int start = 0;

		public RunningAverage(int[] running_average_minutes_array) {
			
			// find maximum size of window
			int local_window_size_max = -1;
			for (int i =0; i<running_average_minutes_array.length; i++) {
				
				if (local_window_size_max < running_average_minutes_array[i]) {
					
					local_window_size_max = running_average_minutes_array[i];
					
				}
				
			}
			
			past_readings = new int[local_window_size_max];
			
		}

		private int recalcuateSum(int window_size) {
			
			//sum the most recent window_size values
			int local_index = start;
			int count = 0;
			int local_sum = 0;
			
			while(count < window_size) {
				
				local_sum += past_readings[local_index];
				local_index--;
				count++;
				
				if (local_index < 0) {

					local_index = past_readings.length-1;
					
				}
				
			}
			
			return(local_sum);
			
		}
		
//		public double updateRunningAverageOriginal(int windspeed) {
//
//			/* Add new sample to Sum; Subtract expiring sample */
//			sum = (sum - past_readings[index]) + windspeed;
//			past_readings[index] = windspeed; // save wind speed
//
//			/* advance index */
//			index++;
//			if (index >= past_readings.length)
//				index = 0;
//
//			
//			/* calculate running average */
//			return (sum / (double) RUNNING_AVERAGE_MINUTES);
//
//		}


		
		
		public double updateRunningAverage(int windspeed, int window_size) {

			if (previousWindowSize != window_size) {
				
				sum = recalcuateSum(window_size);
				
				// adjust tail
				tail = start - window_size;
				if (tail < 0) {tail =  past_readings.length + tail;}
				previousWindowSize = window_size;
			}
			
			/* Add new sample to Sum; Subtract sample that is leaving window */
			sum = (sum - past_readings[tail]) + windspeed;
			past_readings[start] = windspeed; // save wind speed

			/* advance indices */
			if (++start >= past_readings.length) {start = 0;}
			if (++tail >= past_readings.length) {tail = 0;}
			
			/* calculate running average */
			return (sum / (double) window_size);

		}

	}
