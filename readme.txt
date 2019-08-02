# Follow these steps to compile and run simulation for station KPIT.  (Change KPIT to another station to run that station.)
0. javac -version # Check version of javac. The following version works: javac 1.8.0_212 
1. cd src
2. javac edu/pitt/cs/people/guy/wind/benchmarks/*.java -d ../classes
3. cd ../classes
4. java -classpath ".:../resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks KPIT 0.9
