#!/bin/bash

echo "Be sure that iUsedAllItsAllocatedVisibilityMinutesPerMonth=8760 in resources/RetractableHarvesterBenchmarks.properties"

java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false true s > logs/ALL_OLA_Gamma_Static-_Y_step10_Yplus30-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true true s > logs/ALL_OLA_Gamma_Static_with_weather_prediction-_Y_step10_Yplus30-_1_step30_121minutes.log



java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false true a > logs/ALL_OLA_Gamma_Aging-_Y_step10_Yplus30-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true true a > logs/ALL_OLA_Gamma_Aging_with_weather_prediction-_Y_step10_Yplus30-_1_step30_121minutes.log



java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false true f > logs/ALL_OLA_Gamma_Fuzzy-_0.5_membership_only-_1_step30_121minutes.log

    
java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 true true f > logs/ALL_OLA_Gamma_Fuzzy_with_weather_prediction-_0.5_membership_only-_1_step30_121minutes.log

echo "finished"
