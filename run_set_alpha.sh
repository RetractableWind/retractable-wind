#!/bin/bash

echo "Be sure that iUsedAllItsAllocatedVisibilityMinutesPerMonth=51234 in resources/RetractableHarvesterBenchmarks.properties"

java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false false s > logs/ALL_OLA_Alpha_Static-_Y_step10_Yplus30-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true false s > logs/ALL_OLA_Alpha_Static_with_weather_prediction-_Y_step10_Yplus30-_1_step30_121minutes.log



java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false false a > logs/ALL_OLA_Alpha_Aging-_Y_step10_Yplus30-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true false a > logs/ALL_OLA_Alpha_Aging_with_weather_prediction-_Y_step10_Yplus30-_1_step30_121minutes.log



java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false false f > logs/ALL_OLA_Alpha_Fuzzy-_0.5_membership_only-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true false f > logs/ALL_OLA_Alpha_Fuzzy_with_weather_prediction-_0.5_membership_only-_1_step30_121minutes.log

echo "finished"
