# Follow these steps to compile and run simulation for station KPIT.  (Change KPIT to another station to run that station. Change KPIT to ALL to run all stations. Note all command line parameters.)

0. Ensure that KPIT's training and testing windspeed data files are in the directory named "resources". The training and testing files have the name pattern
"training<STATION>2004-2012in.csv" and
"testing<STATION>2013-2014in.csv", respectively.  If those files are not in the resources directory, copy them from the zip file "training_and_testing_wind_data_for_all_30_stations.zip" at https://web.archive.org/web/20251213063038/https://d-scholarship.pitt.edu/37697/ or https://zenodo.org/records/20634733. 

1. javac -version # Check version of javac. The following version works: javac 25.0.3 

2. Inspect training ranges and steps in src/edu/pitt/cs/people/guy/wind/benchmark/Static.java, Aging.java, and/or Fuzzy*.java.  In Static.java and Aging.java, search for each of the following five constants:
    a. final int RUNNING_AVERAGE_MINUTES_START =
    b. final int RUNNING_AVERAGE_MINUTES_STEP =
    c. final int  RUNNING_AVERAGE_MINUTES_END =
    d. final int Y_INTERCEPT_STEP =
    e. Y_INTERCEPT_END =

In Fuzzy*.java, instead of searching for Y_INTERCEPT_STEP and Y_INTERCEPT_END, search for the following:
    d. final double DEPLOYMENT_THRESHOLD_MV_STEP =
    e. final double DEPLOYMENT_THRESHOLD_MV_END =
    f. final double DEPLOYMENT_THRESHOLD_MV_START =

For each of the constants above, where applicable, adjust its value or expression to explore ranges as desired.

3. Ensure that your shell's working directory is `retractable-wind`.

4. At your shell's prompt run `javac src/edu/pitt/cs/people/guy/wind/benchmarks/*.java -d classes`

5. Inspect the `iUsedAllItsAllocatedVisibilityMinutesPerMonth` setting in `resources/RetractableHarvesterBenchmarks.properties`.  Ensure that the number of minutes that the harvester is permitted to be visible is correct.  For example, for OLA set Alpha, use 51234 minutes, and for OLA sets Beta and Gamma, use 8760 minutes.

6. Start training and testing (while redirecting output to a log file).
    Abstraction: `java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks <station> <lamda> <use_weather_prediction> <limit_transitions> <algorithm> > <log_file>`
    First Example: `java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks KPIT 0.9 false false s > logs/KPIT_OLA_Alpha_Static-_Y_step10_Yplus30knots-_1_step30_121minutes.log`
    Second Example: `java -classpath "classes:resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks ALL 0.9 false false a > logs/ALL_OLA_Alpha_Aging-_Y_step10_Yplus30knots-_1_step30_121minutes.log`
    (To run all three algorithms, Static, Aging, and Fuzzy, with and without weather prediction, for the same set of operation limitation agreements, consider inspecting and using the following supplied shell scripts: `run_set_alpha.sh`, `run_set_beta.sh`, and `run_set_gamma.sh`.)

7. Extract results from the log file: Ensure that the working directory of your shell is the `scripts` directory.  At your shell prompt, run `python3 extract_results_from_log.py <log_file>` where <log_file> is the name of the log file _not_ including its path.
    Example: `. . . :~/retractable-wind/scripts$ python3 extract_results_from_log.py specialALL_OLA_Beta_Aging-_Y_step1_Yplus7-_1_step1_361minutes.log`
    Check the `../reports` directory for two results files, which have basename of <log_file> and the suffixes `_MQNetNorm.csv` and `_NetNorm.csv`
    Continuing the example: The two reports are named `specialALL_OLA_Beta_Aging-_Y_step1_Yplus7-_1_step1_361minutes_MQNetNorm.csv` and `specialALL_OLA_Beta_Aging-_Y_step1_Yplus7-_1_step1_361minutes_NetNorm.csv`
    (Consider using the script `extract_from_all_logs.sh` which is in the `scripts` directory or using a similar script to extract from multiple log files.)

8. Analyze the .csv file(s) using R.  To generate one or more box plots, edit the R script `box_plot.R` as needed.  For example, to generate a box plot for the NetNorm scores in `specialALL_OLA_Beta_Aging-_Y_step1_Yplus7-_1_step1_361minutes_NetNorm.csv` use the following assignments: `OLA_SET = "Beta"` and `regular_expression <- paste0("^specialALL_OLA_", OLA_SET, ".*_", METRIC, "\\.csv")`.  Ensure that `boxplot_output_filename` has the name that you want.
    Similarly, edit `mean.R` as needed.
    (If you are unfamilar with R, you may need to learn what the `library()` statements in the scripts do and learn about R packages to install the packages that the R scripts use.)
    Note that `regular_expression` in each provided R script allows you analyze .csv files of the same `OLA_SET` and `METRIC` and prefix in parallel, which causes `box_plot.R` to put box plots in a single chart.
    Be sure to check `box_plot.R` and `mean.R` before running them.
