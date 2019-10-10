# Follow these steps to compile and run simulation for station KPIT.
(Change KPIT to another station to run that station. Note all command
line parameters.)
0. Ensure that KPIT's training and testing windspeed data files are in the directory named
"resources". The training and testing files have the name pattern
"training<STATION>2004-2012in.csv" and
"testing<STATION>2013-2014in.csv", respectively.  If those files are
not in the resources directory, copy them from the zip file at http://d-scholarship.pitt.edu/37697/
having the description "training and testing windspeed for all 30 stations". 
1. javac -version # Check version of javac. The following version works: javac 1.8.0_212 
2. cd src
3. javac edu/pitt/cs/people/guy/wind/benchmarks/*.java -d ../classes
4. java -classpath "../classes:../resources" edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks KPIT 0.9 true true s

