package edu.pitt.cs.people.guy.wind.benchmarks;

import java.util.ArrayList;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
//import java.util.Set;
import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.LinkedList;

import edu.pitt.cs.people.guy.wind.benchmarks.OptimumAlgorithm.Vertex.Label;
import edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks.*;

/* Deploy or retract must be called every minute */
class OptimumAlgorithm {

	long zetaPlus = Long.MAX_VALUE;

	int DEPLOYMENT_THRESHOLD;
	int RETRACTION_THRESHOLD;

	boolean bPrintTracerCode = false;

	HarvesterModel harvester;
	Vertex destinationVertex;
	
	int mostRecentLineWhereZetaPlusWasUpdated = 0; // "1." prefix is assumed
	Vertex mostRecentLineWhereZetaPlusWasUpdatedParent = null;
	Vertex mostRecentLineWhereZetaPlusWasUpdatedChild = null;

	Workload ws = null;

	public long maximumNetEnergyGainKwh = -1; // will be updated after all wind speed samples have been found


	class ExitingEdge {
		public int timestepOfNextVertex = 0;
		public long visibilityUsedMinutes = 0;
		public long netEnergyGainKwh = 0;
		public long invertedNetEnergyGainKwh = -1; // will be changed after maximum netEnergyGainKwh is found

	}
	
	class ScanEligibleLabel {

		public Vertex id; // vertex being labeled
		public Vertex.Label label;
		
		public ScanEligibleLabel(Vertex localID, Label localLabel) {
			
			id = localID;
			label = localLabel;
			
		}

		
	}
	
	//ArrayList<Vertex> listRetractedVertex = new ArrayList<Vertex>();
	LinkedList<Vertex.Label> queueSE = new LinkedList<Vertex.Label>();


	class Vertex {

		public int timestep;
		public ExitingEdge keepingStateEdge = new ExitingEdge();
		public ExitingEdge changingStateEdge = new ExitingEdge(); // upgrade: only create edges when needed

		public Vertex keepingStateParent = null;
		public Vertex changingStateParent = null;

		// zeta variables from the replenishment algorithm
		// zeta variables pertain to the path of least cost
		long zetaCostOfLeastCostPathForwardFromS = Long.MAX_VALUE;
		long zetaWeightOfLeastCostPathForwardFromS = Long.MAX_VALUE;
		boolean bZetaWeightLimitViolatedForwardFromS = false;
		
		long zetaCostOfLeastCostPathBackwardFromT = Long.MAX_VALUE;
		long zetaWeightOfLeastCostPathBackwardFromT = Long.MAX_VALUE;
		boolean bZetaWeightLimitViolatedBackwardFromT = false;

		// omega variables from the replenishment algorithm
		// omega variables pertain to the path of least weight path
		long omegaCostOfTheLeastWeightPathForwardFromS;
		long omegaCostOfTheLeastWeightPathBackwardFromT;
		long omegaWeightOfTheLeastWeightPathForwardFromS;
		long omegaWeightOfTheLeastWeightPathBackwardFromT;

		// cs,i = the cost of the least weight path from s to i
		public LocalDateTime localDateTime;

		boolean bIsolated = false;

		boolean bSettled = false; // for use in the shortest path algorithm
		boolean bSettledBackward = false; // for use in the shortest path algorithm

		boolean bDeployed = false;

		Vertex zetaCostOfLeastCostPathForwardFromSUpdating = null;

		// Vertex zetaCostOfLeastCostPathBackwardFromTChild = null;

		//Vertex zetaCostOfLeastCostPathBackwardFromTParent = null; // determine when traversing the tree from T to S
		Vertex zetaCostOfLeastCostPathBackwardFromTChild = null;
		
		Vertex zetaCostOfLeastCostPathBackwardFromTUpdating = null;

		boolean bReplenishment = false;

		int windspeed = 0;

		Vertex zetaCostOfLeastCostPathForwardFromSParent = null;
		
		
		
		public class Label {
			
			public long costLabel; // c subscript L
			public long weightLabel; // w subscript L
			public Vertex.Label parentLabel = null;
			public Vertex id;
			//public boolean bScanEligible = true;
			
			/*
			 *  Constructor
			 */
			Label (long localCostLabel, long localWeightLabel, Label localParentLabel,
					Vertex localId) {
				
				costLabel = localCostLabel; // c subscript L
				weightLabel = localWeightLabel; // w subscript L
				parentLabel = localParentLabel;
				id = localId; // vertex being labeled by this label
				
			}
			
			
		}
		
		ArrayList<Label> listLabel = new ArrayList<Label>();
	
	}

	ArrayList<Vertex> listDeployedVertex = new ArrayList<Vertex>();

	ArrayList<Vertex> listRetractedVertex = new ArrayList<Vertex>();

	
	// adapted from code published on a web site
	public class ComparatorCostThenWeight implements Comparator<Vertex.Label> {

		// ascending
		public int compare(Vertex.Label x, Vertex.Label y) {
			if (x.costLabel < y.costLabel) {
				return -1;
			} else if (x.costLabel > y.costLabel) {
				return 1;
			}
			// if tied, compare weight
			if (x.weightLabel < y.weightLabel) {
				
				return -1;
				
			} else if (y.weightLabel < x.weightLabel) {
				
				return 1;
				
			}
			return 0;
		}

	}
	
	// adapted from code published on a web site
	class ComparatorZetaCostForward implements Comparator<Vertex> {

		public int compare(Vertex x, Vertex y) {
			if (x.zetaCostOfLeastCostPathForwardFromS < y.zetaCostOfLeastCostPathForwardFromS) {
				return -1;
			} else if (x.zetaCostOfLeastCostPathForwardFromS > y.zetaCostOfLeastCostPathForwardFromS) {
				return 1;
			}
			return 0;
		}

	}

	// adapted from code published on a web site
	class ComparatorOmegaWeightForward implements Comparator<Vertex> {

		public int compare(Vertex x, Vertex y) {
			if (x.omegaWeightOfTheLeastWeightPathForwardFromS < y.omegaWeightOfTheLeastWeightPathForwardFromS) {
				return -1;
			} else if (x.omegaWeightOfTheLeastWeightPathForwardFromS > y.omegaWeightOfTheLeastWeightPathForwardFromS) {
				return 1;
			}
			return 0;
		}

	}

	// adapted from code published on a web site
	class ComparatorZetaCostBackward implements Comparator<Vertex> {

		public int compare(Vertex x, Vertex y) {
			if (x.zetaCostOfLeastCostPathBackwardFromT < y.zetaCostOfLeastCostPathBackwardFromT) {
				return -1;
			} else if (x.zetaCostOfLeastCostPathBackwardFromT > y.zetaCostOfLeastCostPathBackwardFromT) {
				return 1;
			}
			return 0;
		}

	}

	private void printTransitions(Vertex previousVertex, Vertex vertex) {

		if ((previousVertex == null) && (vertex == null))
		{
			System.out.println("Error: Both vertices in path are null.");
			System.exit(0);
		}
		
		if (previousVertex == null) {

			System.out.println("\nStart: " + (vertex.bDeployed ? "D" : "R") + vertex.timestep + "(" + vertex.localDateTime + ")");

		} else if (vertex == null) {

			System.out.println("End: " + (previousVertex.bDeployed ? "D" : "R") + previousVertex.timestep + "("
					+ previousVertex.localDateTime + ")");
		} else if ((previousVertex.bDeployed != vertex.bDeployed)) {

			System.out.println((previousVertex.bDeployed ? "D" : "R") + previousVertex.timestep + "("
					+ previousVertex.localDateTime + ")");
			System.out.println(" to ");
			System.out.println((vertex.bDeployed ? "D" : "R") + vertex.timestep + "(" + vertex.localDateTime + ")");

		}

	}

	BufferedWriter bufferedWriter;
	//private boolean restoreFromOrPrepareToCheckpoint() {
	private boolean bPreviousAttemptToFindOptimumBegun() {
		
		boolean bPreviousAttemptBegun = false;
		String TRAINING_WINDSPEED_FILENAME = ws.getTRAINING_WINDSPEED_FILENAME();
		
		/* Create copy file of minute-by-minute windspeed samples */
		String[] tokens = TRAINING_WINDSPEED_FILENAME.split("\\.(?=[^\\.]+$)"); // adapted from answer posted on stackoverflow
		String FILENAME_BASE = tokens[0].substring(1); //omit leading backslash 
		String FILENAME_EXTENSION = tokens[1];
		
		String filename = FILENAME_BASE + "OptimumForCheckpoint." + FILENAME_EXTENSION;		

		File file;
		
		try {
			file = new File(filename);
			
			if (file.exists()) {
			
				bPreviousAttemptBegun = true;
				// TODO:
				
				
				
				
			}	
			else {
				
				file.createNewFile();		
				
			}
			
			FileWriter fileWriter = new FileWriter(file);
			bufferedWriter = new BufferedWriter(fileWriter);
			
		} catch (IOException e) {
			e.printStackTrace();
		}

		return(bPreviousAttemptBegun);		
		
	}
	
	
	private void savePertinentInformationOfVertex(Vertex vertex) {
		
		
		//System.out.println("Checkpointing " + vertex.bDeployed + vertex.timestep);
		
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		
		
		String vertexLine = sdf.format(cal.getTime()) + "," +
							vertex.timestep + "," +
							vertex.bDeployed + "," +
							vertex.zetaCostOfLeastCostPathForwardFromS + "," +
							vertex.zetaWeightOfLeastCostPathForwardFromS + "," +
							vertex.bZetaWeightLimitViolatedForwardFromS  + ",";
		
		if (vertex.zetaCostOfLeastCostPathForwardFromSParent == null) {
			
			vertexLine = vertexLine + "null,null\n";
			
		} else {
			
			vertexLine = vertexLine + 
					vertex.zetaCostOfLeastCostPathForwardFromSParent.timestep + "," +
					vertex.zetaCostOfLeastCostPathForwardFromSParent.bDeployed + "\n";
					
		}
												
		try {
			bufferedWriter.write(vertexLine);
			bufferedWriter.flush();
			
		} catch (IOException e) {

			e.printStackTrace();
		}
		

		
	}
		
	private boolean calculateShortestPathFromSourceCostDijkstra() {
	    
		//source.setDistance(0); // Assume that the harvester starts retracted.  This the source is
		Vertex vertex = listRetractedVertex.get(0);
		
		vertex.zetaCostOfLeastCostPathForwardFromS = 0;
		vertex.zetaWeightOfLeastCostPathForwardFromS = 0;
		
	    // Set<Vertex> settledNodes = new HashSet<Vertex>();
	    // Set<Vertex> unsettledNodes = new HashSet<Vertex>();
	 
	    /* "We add the startNode to the unsettled nodes set." */
	    // unsettledNodes.add(retracted);
	    /* "Choose an evaluation node from the unsettled nodes set, 
	     * the evaluation node should be the one with the lowest distance from the source." */
	    // The lowest distance from node will be the deployed node if it is available
	    
		// Visit
		
		final int PRIORITY_QUEUE_CAPACITY = 7;
		Comparator<Vertex> queueComparator = new ComparatorZetaCostForward();
		PriorityQueue<Vertex> priorityQueue = new PriorityQueue<Vertex>(PRIORITY_QUEUE_CAPACITY,
				queueComparator);
		// Add dummy node to top of the tree to allow 
		//  that edits of real nodes in the queue will cause the priority queue to
		//  prioritize correctly

		// Check whether the attempt is in progress
		if (bPreviousAttemptToFindOptimumBegun()) {
			
			// TODO: re-populate beginning of grid
			System.out.println("I have to re-populate the grid.");
			
			
		}
		
		while (vertex != null) {

			vertex.bSettled = true; // Vertex is settled; Thus, set weight flag
			// Save the settled node's pertinent information
			savePertinentInformationOfVertex(vertex);
			
			if (vertex.zetaWeightOfLeastCostPathForwardFromS > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
				
				vertex.bZetaWeightLimitViolatedForwardFromS = true;
				setZetaWeightLimitViolatedForwardFromSFlagBackwardDijkstra(vertex);
				
			}			

			if (bPrintTracerCode) {
				char dOrR = vertex.bDeployed ? 'D' : 'R';
				System.out.print("calculateShortestPathFromSourceCostDijkstra " + dOrR + vertex.timestep
						+ " zetaCostOfLeastCostPathForwardFromS: " + 
						vertex.zetaCostOfLeastCostPathForwardFromS
						+ " Weight: "
						+ vertex.zetaWeightOfLeastCostPathForwardFromS + " vs. Weight Limit: "
						+ ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth
						);
				if (vertex.zetaCostOfLeastCostPathForwardFromSParent != null) {
					System.out.println(" Parent: " + (vertex.zetaCostOfLeastCostPathForwardFromSParent.bDeployed ? 'D' : 'R') + 
						vertex.zetaCostOfLeastCostPathForwardFromSParent.timestep);
				} else {
					System.out.println(" Parent: null");
				}
			}			
									
			if (vertex.changingStateEdge != null) {
				Vertex nextVertexCandidateViaChangingSE = null;
				int i  = vertex.changingStateEdge.timestepOfNextVertex;
				nextVertexCandidateViaChangingSE = (vertex.bDeployed) ? 
						listRetractedVertex.get(i) : listDeployedVertex.get(i);
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				long preliminaryDistance = vertex.zetaCostOfLeastCostPathForwardFromS + 
						vertex.changingStateEdge.invertedNetEnergyGainKwh;
				long preliminaryWeight = vertex.zetaWeightOfLeastCostPathForwardFromS +
						vertex.changingStateEdge.visibilityUsedMinutes;
				
				
				if (nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					nextVertexCandidateViaChangingSE.zetaWeightOfLeastCostPathForwardFromS = preliminaryWeight;		
					priorityQueue.add(nextVertexCandidateViaChangingSE);
					
				} else if (preliminaryDistance <
						nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
						nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
						nextVertexCandidateViaChangingSE.zetaCostOfLeastCostPathForwardFromSParent = vertex;
						nextVertexCandidateViaChangingSE.zetaWeightOfLeastCostPathForwardFromS = preliminaryWeight;
						Vertex v1 = priorityQueue.peek();
						//System.out.println("Test: " + v1.bDeployed + v1.timestep);
						// rebuild the priority queue?
						priorityQueue.remove(nextVertexCandidateViaChangingSE);
						priorityQueue.add(nextVertexCandidateViaChangingSE);
					}							
				
			}						


			if (vertex.keepingStateEdge != null) {

				Vertex nextVertexCandidateViaKeepingSE = null;
				int i  = vertex.keepingStateEdge.timestepOfNextVertex;
				nextVertexCandidateViaKeepingSE = (vertex.bDeployed) ? 
						listDeployedVertex.get(i) : listRetractedVertex.get(i);
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				long preliminaryDistance = vertex.zetaCostOfLeastCostPathForwardFromS + 
								vertex.keepingStateEdge.invertedNetEnergyGainKwh;
				long preliminaryWeight = vertex.zetaWeightOfLeastCostPathForwardFromS +
						vertex.keepingStateEdge.visibilityUsedMinutes;
				
				if (nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromS = 
							vertex.zetaCostOfLeastCostPathForwardFromS + 
							vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					nextVertexCandidateViaKeepingSE.zetaWeightOfLeastCostPathForwardFromS = preliminaryWeight;
					priorityQueue.add(nextVertexCandidateViaKeepingSE);
					
				} if (preliminaryDistance <
						nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
						nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
						nextVertexCandidateViaKeepingSE.zetaCostOfLeastCostPathForwardFromSParent = vertex;
						nextVertexCandidateViaKeepingSE.zetaWeightOfLeastCostPathForwardFromS = preliminaryWeight;
						Vertex v1 = priorityQueue.peek();
						//System.out.println("Test: " + v1.bDeployed + v1.timestep);
						// rebuild the priority queue?
						priorityQueue.remove(nextVertexCandidateViaKeepingSE);
						priorityQueue.add(nextVertexCandidateViaKeepingSE);
						
					}
			}

			// Get the closest vertex to the source  /* TODO: work out the linking
			Vertex nextVertex = priorityQueue.poll();
			vertex = nextVertex;

		}
	    
		// Check how many nodes are not settled.
		int nodesNotSettled = 0;
		for (Vertex v : listRetractedVertex) {

			if (v.bSettled == false) {
				nodesNotSettled++;
				}
			
		}
		for (Vertex v : listDeployedVertex) {

			if (v.bSettled == false) {
				nodesNotSettled++;
				}
			
		}
		
		System.out.println("The number of NOT settled nodes are " + nodesNotSettled);
		
		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return ((destinationVertex.bZetaWeightLimitViolatedForwardFromS == false)
				&& (destinationVertex.zetaWeightOfLeastCostPathForwardFromS < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth));
		
		
	}

	

	private boolean calculateShortestPathFromDestinationCostDijkstra() {
	    
		//source.setDistance(0); // Assume that the harvester starts retracted.  This the source is
		Vertex vertex = listRetractedVertex.get(listRetractedVertex.size()-1);
				
		vertex.zetaCostOfLeastCostPathBackwardFromT = 0;
		vertex.zetaWeightOfLeastCostPathBackwardFromT = 0;

		
		
	    // Set<Vertex> settledNodes = new HashSet<Vertex>();
	    // Set<Vertex> unsettledNodes = new HashSet<Vertex>();
	 
	    /* "We add the startNode to the unsettled nodes set." */
	    // unsettledNodes.add(retracted);
	    /* "Choose an evaluation node from the unsettled nodes set, 
	     * the evaluation node should be the one with the lowest distance from the source." */
	    // The lowest distance from node will be the deployed node if it is available
	    
		// Visit
		
		final int PRIORITY_QUEUE_CAPACITY = 7;
		Comparator<Vertex> queueComparator = new ComparatorZetaCostBackward();
		PriorityQueue<Vertex> priorityQueue = new PriorityQueue<Vertex>(PRIORITY_QUEUE_CAPACITY,
				queueComparator);

		
		while (vertex != null) {
			
			vertex.bSettled = true;
			if (vertex.zetaWeightOfLeastCostPathBackwardFromT > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
				
				vertex.bZetaWeightLimitViolatedBackwardFromT = true;
				setZetaWeightLimitViolatedBackwardFromTFlagForwardDijkstra(vertex);
				
			}
			
			if (bPrintTracerCode) {
				char dOrR = vertex.bDeployed ? 'D' : 'R';
				System.out.print("calculateShortestPathFromDestinationCostDijkstra " + dOrR + vertex.timestep
						+ " zetaCostOfLeastCostPathBackwardFromT: " + 
						vertex.zetaCostOfLeastCostPathBackwardFromT
						 + " Weight: "
						 + vertex.zetaWeightOfLeastCostPathBackwardFromT + " vs. Weight Limit: "
						 + ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth
						);
				if (vertex.zetaCostOfLeastCostPathBackwardFromTChild != null) {
					System.out.println(" Child: " + (vertex.zetaCostOfLeastCostPathBackwardFromTChild.bDeployed ? 'D' : 'R') + 
						vertex.zetaCostOfLeastCostPathBackwardFromTChild.timestep);
				} else {
					System.out.println(" Child: null");
				}
			}			
			

			if (vertex.changingStateParent != null) {

				// If candidate for closest vertex has not yet been added to priority queue, add it
				long preliminaryDistance = vertex.zetaCostOfLeastCostPathBackwardFromT + 
						vertex.changingStateParent.changingStateEdge.invertedNetEnergyGainKwh;
				long preliminaryWeight = vertex.zetaWeightOfLeastCostPathBackwardFromT +
						vertex.changingStateParent.changingStateEdge.visibilityUsedMinutes;
				
				if (vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT == Long.MAX_VALUE) {
					
					vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT = preliminaryDistance;
					vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromTChild = vertex;
					vertex.changingStateParent.zetaWeightOfLeastCostPathBackwardFromT = preliminaryWeight;		
					priorityQueue.add(vertex.changingStateParent);
					
				} else if (preliminaryDistance <
						vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT) {
						// update distance
						vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT = preliminaryDistance;									
						vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromTChild = vertex;
						vertex.changingStateParent.zetaWeightOfLeastCostPathBackwardFromT = preliminaryWeight;
						// essentially refresh the priority queue
						priorityQueue.remove(vertex.changingStateParent);
						priorityQueue.add(vertex.changingStateParent);
					}							
				
			}						


			if (vertex.keepingStateParent != null) {

				// If candidate for closest vertex has not yet been added to priority queue, add it
				long preliminaryDistance = vertex.zetaCostOfLeastCostPathBackwardFromT + 
						vertex.keepingStateParent.keepingStateEdge.invertedNetEnergyGainKwh;
				long preliminaryWeight = vertex.zetaWeightOfLeastCostPathBackwardFromT +
						vertex.keepingStateParent.keepingStateEdge.visibilityUsedMinutes;
				
				if (vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT == Long.MAX_VALUE) {
					vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT = 
							vertex.zetaCostOfLeastCostPathBackwardFromT + 
							vertex.keepingStateParent.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromTChild = vertex;
					vertex.keepingStateParent.zetaWeightOfLeastCostPathBackwardFromT = preliminaryWeight;
					priorityQueue.add(vertex.keepingStateParent);
					
				} if (preliminaryDistance <
						vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT) {
						// update distance
						vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT = preliminaryDistance;									
						vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromTChild = vertex;
						vertex.keepingStateParent.zetaWeightOfLeastCostPathBackwardFromT = preliminaryWeight;
						Vertex v1 = priorityQueue.peek();
						//System.out.println("Test: " + v1.bDeployed + v1.timestep);
						// rebuild the priority queue?
						priorityQueue.remove(vertex.keepingStateParent);
						priorityQueue.add(vertex.keepingStateParent);
						
					}
			}

			// Get the closest vertex to the source  /* TODO: work out the linking
			Vertex nextVertex = priorityQueue.poll();
			vertex = nextVertex;
			
		}
	    
		// Check how many nodes are not settled.
		int nodesNotSettled = 0;
		for (Vertex v : listRetractedVertex) {

			if (v.bSettled == false) {
				nodesNotSettled++;
				}
			
		}
		for (Vertex v : listDeployedVertex) {

			if (v.bSettled == false) {
				nodesNotSettled++;
				}
			
		}
		
		System.out.println("The number of NOT settled nodes are " + nodesNotSettled);
		
		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return ((destinationVertex.bZetaWeightLimitViolatedBackwardFromT == false)
				&& (destinationVertex.zetaWeightOfLeastCostPathBackwardFromT < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth));
		
		
	}


	
	
	
	
	/* For each vertex in the graph, calc. shortest path from source cost */
	private boolean calculateShortestPathFromSourceCostNoDeploymentCost(boolean bPrintTransitions) {

		boolean bZetaWeightLimitViolatedForwardFromSHistory = false;

		// source.setDistance(0); // Assume that the harvester starts retracted. This
		// the source is
		Vertex vertex = listRetractedVertex.get(0);

		vertex.zetaWeightOfLeastCostPathForwardFromS = 0;
		vertex.zetaCostOfLeastCostPathForwardFromS = 0;

		long sumWeight = 0;
		long sumCost = 0;

		Vertex previousVertex = null;
		if (bPrintTransitions) {

			System.out.print("OptimumPathCostForward: ");
			printTransitions(previousVertex, vertex);

		}
		previousVertex = vertex;
		
		// The least cost path will be the one that is always deployed when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// deployed as possible.
		while (vertex != null) {

			if (vertex.timestep == 0) {
				
				System.out.println("Pausing at 0.");
				
			}
			
			// Take the deployed edge forward if it exists
			if (!vertex.bDeployed) { // retracted vertex

				if (vertex.changingStateEdge != null) { // get deployed vertex

					// Also, set the distances to the retracted vertex
					// before setting the distances to the deployed vertex
					if (vertex.keepingStateEdge != null) {
						Vertex retractedVertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);
						retractedVertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight
								+ vertex.keepingStateEdge.visibilityUsedMinutes;
						if (retractedVertex.zetaWeightOfLeastCostPathForwardFromS > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

							retractedVertex.bZetaWeightLimitViolatedForwardFromS = true;

						}
						retractedVertex.zetaCostOfLeastCostPathForwardFromS = sumCost
								+ vertex.keepingStateEdge.invertedNetEnergyGainKwh;
						System.out.println("temp"); //temp for debugging
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					// Set distance to deployed vertex
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else if (vertex.keepingStateEdge != null) { // get retracted vertex

					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else { // current vertex has no exiting edges

					vertex = null; // set next vertex to null

				}

			} else { // vertex is deployed

				if (vertex.keepingStateEdge != null) { // get deployed vertex

					// Also, set the distances to the retracted vertex
					// before setting the distances to the deployed vertex
					if (vertex.changingStateEdge != null) {
						Vertex retractedVertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);
						retractedVertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight
								+ vertex.changingStateEdge.visibilityUsedMinutes;
						if (retractedVertex.zetaWeightOfLeastCostPathForwardFromS > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

							retractedVertex.bZetaWeightLimitViolatedForwardFromS = true;

						}
						retractedVertex.zetaCostOfLeastCostPathForwardFromS = sumCost
								+ vertex.changingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					// Now, get the deployed vertex
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else if (vertex.changingStateEdge != null) { // get retracted vertex
					
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}
			}

			// if next vertex is not null, update its weight and cost. And update
			// it overweight flag.
			if (vertex != null) {

				vertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight;
				vertex.zetaCostOfLeastCostPathForwardFromS = sumCost;

/*				if (vertex.bDeployed) {
					
					System.out.println("For " + (vertex.bDeployed? "D":"R") +  vertex.timestep + ":");
					System.out.println("  The vertex.zetaCostOfLeastCostPathForwardFromS cost is " +
							vertex.zetaCostOfLeastCostPathForwardFromS);
					if (vertex.zetaCostOfLeastCostPathForwardFromS < 0) {
						
						System.out.println("Negative cost");
						System.exit(0);
					}
					
				}*/
				
				vertex.bZetaWeightLimitViolatedForwardFromS = (sumWeight > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
						|| bZetaWeightLimitViolatedForwardFromSHistory;

				// TODO double check that all flags should be set when one node is overweight
				// if path becomes overweight, set overweight flag to destination
				if (!bZetaWeightLimitViolatedForwardFromSHistory && vertex.bZetaWeightLimitViolatedForwardFromS) {

					setZetaWeightLimitViolatedForwardFromSFlagBackward(vertex);

				}
				bZetaWeightLimitViolatedForwardFromSHistory = vertex.bZetaWeightLimitViolatedForwardFromS;

				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

				// tracer code
				// D or R
				if (bPrintTracerCode) {
					char dOrR = vertex.bDeployed ? 'D' : 'R';
					System.out.println("calculateShortestPathFromSourceCost" + dOrR + vertex.timestep
							+ " Overweight Forward: " + vertex.bZetaWeightLimitViolatedForwardFromS + " Weight: "
							+ vertex.zetaWeightOfLeastCostPathForwardFromS + " vs. Weight Limit: "
							+ ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
				}

			}

			// Look for transition between deployed and retracted
			if (bPrintTransitions) {

				printTransitions(previousVertex, vertex);

			}
			previousVertex = vertex;

		}

		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return ((destinationVertex.bZetaWeightLimitViolatedForwardFromS == false)
				&& (destinationVertex.zetaWeightOfLeastCostPathForwardFromS < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth));

	}

	private void setZetaWeightLimitViolatedForwardFromSFlagBackwardDijkstra(Vertex vertex) {

		vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;

		// Follow the parent backwards
		while (vertex != null) {

			// set the weight limit violation flag if weight surpassed
			if (vertex.bZetaWeightLimitViolatedForwardFromS) {
				
				break; // Flag(s) in path backwards already set
				
			}
			vertex.bZetaWeightLimitViolatedForwardFromS = true;
			vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;

			}
		}


	
	
	
	private void setZetaWeightLimitViolatedBackwardFromTFlagForwardDijkstra(Vertex vertex) {

		vertex = vertex.zetaCostOfLeastCostPathBackwardFromTChild;

		// Follow the child forward
		while (vertex != null) {

			// set the weight limit violation flag if weight surpassed
			if (vertex.bZetaWeightLimitViolatedBackwardFromT) {
				
				break; // Flag(s) in path backwards already set
				
			}
			vertex.bZetaWeightLimitViolatedBackwardFromT = true;
			vertex = vertex.zetaCostOfLeastCostPathBackwardFromTChild;

			}
		}

	private void printOptimumCostBackward(Vertex vertex) {

		//Vertex vertex = listRetractedVertex.get(listRetractedVertex.size()-1);
		


		System.out.println("Optimum path (via zetaCostOfLeastCostPathForwardFromSParent):");
		System.out.println((vertex.bDeployed ? "D" : "R") + vertex.timestep);			
		vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;		
		while (vertex != null) {

			System.out.println((vertex.bDeployed ? "D" : "R") + vertex.timestep);			
			vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;

			}
		}

	/* Start printing from start to destination */
	private void printOptimumCostForward(Vertex vertex) {


		//Vertex vertex = listRetractedVertex.get(0);



		System.out.println("Optimum path (via zetaCostOfLeastCostPathBackwardFromTChild):");
		System.out.println((vertex.bDeployed ? "D" : "R") + vertex.timestep);			
		vertex = vertex.zetaCostOfLeastCostPathBackwardFromTChild;
		// Follow the child forward
		while (vertex != null) {

			System.out.println((vertex.bDeployed ? "D" : "R") + vertex.timestep);			
			vertex = vertex.zetaCostOfLeastCostPathBackwardFromTChild;

			}
		}

	
	
	private void setZetaWeightLimitViolatedForwardFromSFlagBackward(Vertex vertex) {

		// The least weight path will be the one that is always retracted when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// retracted as possible.
		while (vertex != null) {

			// Take the deployed edge backwards if it exists
			if (!vertex.bDeployed) {

				if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;

				} else if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;

				} else {

					vertex = null;

				}

			} else {

				if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;

				} else if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;

				} else {

					vertex = null;

				}

			}

			if (vertex != null) {

				// set the weight limit violation flag if weight surpassed
				vertex.bZetaWeightLimitViolatedForwardFromS = true;

				// tracer code
				// D or R
				/*
				 * char dOrR = vertex.bDeployed? 'D' : 'R';
				 * System.out.println("backwards set weight flag:" + dOrR + vertex.timestep +
				 * " Overweight Forward: " + vertex.bZetaWeightLimitViolatedForwardFromS +
				 * " Weight: " + vertex.zetaWeightOfLeastCostPathForwardFromS +
				 * " vs. Weight Limit: " + ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
				 */
			}
		}

	}

	private long calculateShortestPathFromSourceWeight(Vertex vertex, Boolean bPrintTransitions) {

		// source.setDistance(0); // Assume that the harvester starts retracted. This
		// the source is
		//Vertex vertex = listRetractedVertex.get(0);
		
		vertex.omegaWeightOfTheLeastWeightPathForwardFromS = 0;
		vertex.omegaCostOfTheLeastWeightPathForwardFromS = 0;

		long sumWeight = 0;
		long sumCost = 0;

		// The least weight path will be the one that is always retracted when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// retracted as possible.
		while (vertex != null) {

			Vertex previousVertex = null;
			if (bPrintTransitions) {

				System.out.println("OptimumPathWeightForward:");
				printTransitions(previousVertex, vertex);

			}
			previousVertex = vertex;
			
			// Take the retracted edge backwards if it exists
			if (vertex.bDeployed) { // vertex is deployed

				if (vertex.changingStateEdge != null) { // get retracted vertex

					// Also, set the distances to the deployed vertex
					// before setting the distances to the retracted vertex
					if (vertex.keepingStateEdge != null) {
						Vertex deployedVertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);
						deployedVertex.omegaWeightOfTheLeastWeightPathForwardFromS = sumWeight
								+ vertex.keepingStateEdge.visibilityUsedMinutes;

						deployedVertex.omegaCostOfTheLeastWeightPathForwardFromS = sumCost
								+ vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else if (vertex.keepingStateEdge != null) { // get deployed vertex

					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			} else {

				// vertex is retracted
				if (vertex.keepingStateEdge != null) { // get retracted vertex

					// Also, set the distances to the deployed vertex
					// before setting the distances to the retracted vertex
					if (vertex.changingStateEdge != null) {
						Vertex deployedVertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);
						deployedVertex.omegaWeightOfTheLeastWeightPathForwardFromS = sumWeight
								+ vertex.changingStateEdge.visibilityUsedMinutes;

						deployedVertex.omegaCostOfTheLeastWeightPathForwardFromS = sumCost
								+ vertex.changingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else

				if (vertex.changingStateEdge != null) { // get deployed vertex

					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			}

			if (vertex != null) {

				vertex.omegaWeightOfTheLeastWeightPathForwardFromS = sumWeight;
				vertex.omegaCostOfTheLeastWeightPathForwardFromS = sumCost;

				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

			}

		}

		return (sumWeight);

	}

	/*
	 * Use this function to set the overweight flag to true on the least cost path
	 * going forward from the passed argument
	 */
	private void setZetaWeightLimitViolatedBackwardFromTFlagFoward(Vertex vertex) {

		while (vertex != null) {

			// Take the deployed edge forward if it exists
			if (!vertex.bDeployed) { // vertex is retracted

				if (vertex.changingStateEdge != null) {

					// get deployed vertex
					vertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else if (vertex.keepingStateEdge != null) {

					// get retracted vertex
					vertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			} else { // vertex is deployed

				if (vertex.keepingStateEdge != null) {

					// get deployed vertex
					vertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else

				// get retracted vertex
				if (vertex.changingStateEdge != null) {

					vertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			}

			if (vertex != null) {

				vertex.bZetaWeightLimitViolatedBackwardFromT = true;

				// tracer code
				// D or R
				/*
				 * char dOrR = vertex.bDeployed? 'D' : 'R';
				 * System.out.println("forwards set weight flag:" + dOrR + vertex.timestep +
				 * " Overweight Backward: " + vertex.bZetaWeightLimitViolatedBackwardFromT +
				 * " Weight: " + vertex.zetaWeightOfLeastCostPathBackwardFromT +
				 * " vs. Weight Limit: " + ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
				 */

			}

		}

	}


	
	
/*	
	private boolean calculateShortestPathFromDestinationCost(boolean bPrintTransitions) // reset flag if first run
	// set flag for second run if found to be violated on first run
	{

		boolean bZetaWeightLimitViolatedBackwardFromTHistory = false;

		int indexOfDestinationVertex = listRetractedVertex.size() - 1;
		Vertex vertex = listRetractedVertex.get(indexOfDestinationVertex);

		vertex.zetaWeightOfLeastCostPathBackwardFromT = 0;
		vertex.zetaCostOfLeastCostPathBackwardFromT = 0;

		long sumWeight = 0;
		long sumCost = 0;

		// Look for transition between deployed and retracted
		Vertex previousVertex = null;
		if (bPrintTransitions) {

			System.out.print("OptimumPathCostBackward: ");
			printTransitions(previousVertex, vertex);

		}
		previousVertex = vertex;

		// The least weight path will be the one that is always retracted when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// retracted as possible.
		while (vertex != null) {

			// Take the deployed edge backwards if it exists
			if (!vertex.bDeployed) { // vertex is retracted

				if (vertex.changingStateParent != null) { // get deployed parent

					// Also, set the distances to the retracted parent vertex
					// before setting the distances to the deployed parent vertex
					if (vertex.keepingStateParent != null) {

						vertex.keepingStateParent.zetaWeightOfLeastCostPathBackwardFromT = sumWeight
								+ vertex.keepingStateParent.keepingStateEdge.visibilityUsedMinutes;

						if (vertex.keepingStateParent.zetaWeightOfLeastCostPathForwardFromS > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

							vertex.keepingStateParent.bZetaWeightLimitViolatedBackwardFromT = true;

						}
						vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT = sumCost
								+ vertex.keepingStateParent.keepingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					vertex = vertex.changingStateParent;
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;

				} else if (vertex.keepingStateParent != null) { // get retracted parent

					vertex = vertex.keepingStateParent;
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;

				} else { // current vertex is an orphan

					vertex = null; // set next vertex to null

				}

			} else { // vertex is deployed

				if (vertex.keepingStateParent != null) { // get deployed parent

					// Also, set the distances to the retracted parent vertex
					// before setting the distances to the deployed parent vertex
					if (vertex.changingStateParent != null) {

						vertex.changingStateParent.zetaWeightOfLeastCostPathBackwardFromT = sumWeight
								+ vertex.changingStateParent.changingStateEdge.visibilityUsedMinutes;

						if (vertex.changingStateParent.zetaWeightOfLeastCostPathForwardFromS > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

							vertex.changingStateParent.bZetaWeightLimitViolatedBackwardFromT = true;

						}
						vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT = sumCost
								+ vertex.changingStateParent.changingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					vertex = vertex.keepingStateParent;
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;

				} else if (vertex.changingStateParent != null) { // get retracted parent

					vertex = vertex.changingStateParent;
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;

				} else { // deployed vertex is an orphan

					vertex = null;

				}

			}

			if (vertex != null) {

				vertex.zetaWeightOfLeastCostPathBackwardFromT = sumWeight;
				vertex.zetaCostOfLeastCostPathBackwardFromT = sumCost;

				// set the weight limit violation flag if weight surpassed
				vertex.bZetaWeightLimitViolatedBackwardFromT = (sumWeight > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
						|| bZetaWeightLimitViolatedBackwardFromTHistory;

				// TODO double check that all flags should be set when one node is overweight
				// if path becomes overweight, set overweight flag to destination
				if (!bZetaWeightLimitViolatedBackwardFromTHistory && vertex.bZetaWeightLimitViolatedBackwardFromT) {

					setZetaWeightLimitViolatedBackwardFromTFlagFoward(vertex);

				}
				bZetaWeightLimitViolatedBackwardFromTHistory = vertex.bZetaWeightLimitViolatedBackwardFromT;

				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

				if (bPrintTracerCode) {
					// tracer code
					// D or R
					char dOrR = vertex.bDeployed ? 'D' : 'R';
					System.out.println("calculateShortestPathFromDestinationCost" + dOrR + vertex.timestep
							+ " Overweight Backward: " + vertex.bZetaWeightLimitViolatedBackwardFromT + " Weight: "
							+ vertex.zetaWeightOfLeastCostPathBackwardFromT + " vs. Weight Limit: "
							+ ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
				}

			} // end if: is next vertex null?

			// Look for transition between deployed and retracted
			if (bPrintTransitions) {

				printTransitions(previousVertex, vertex);

			}
			previousVertex = vertex;

		}

		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		Vertex sourceVertex = listRetractedVertex.get(0);
		return ((sourceVertex.bZetaWeightLimitViolatedBackwardFromT == false)
				&& (sourceVertex.zetaWeightOfLeastCostPathBackwardFromT <= ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth));

	}
*/

	private long calculateShortestPathFromDestinationWeight(Vertex vertex,
			boolean bPrintTransitions) {


		if ((vertex.changingStateParent == null) &&
				(vertex.keepingStateParent == null)) {
			
			System.out.print("Error: Destiantion vertex has been orphaned.");
			System.exit(0);
			
		}
		
		vertex.omegaWeightOfTheLeastWeightPathBackwardFromT = 0;
		vertex.omegaCostOfTheLeastWeightPathBackwardFromT = 0;

		long sumWeight = 0;
		long sumCost = 0;

		Vertex previousVertex = null;
		if (bPrintTransitions) {

			System.out.println("OptimumPathWeightBackward:");
			printTransitions(previousVertex, vertex);

		}
		previousVertex = vertex;

		
		// The least weight path will be the one that is always retracted when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// retracted as possible.
		while (vertex != null) {
			


			// Take the retracted edge backwards if it exists
			if (vertex.bDeployed) { // vertex is deployed

				// Also, set the distances to the deployed parent vertex
				// before setting the distances to the retracted parent vertex
				if (vertex.keepingStateParent != null) {

					vertex.keepingStateParent.omegaWeightOfTheLeastWeightPathBackwardFromT = sumWeight
							+ vertex.keepingStateParent.keepingStateEdge.visibilityUsedMinutes;

					vertex.keepingStateParent.omegaCostOfTheLeastWeightPathBackwardFromT = sumCost
							+ vertex.keepingStateParent.keepingStateEdge.invertedNetEnergyGainKwh;
				}
				// /\ End of the setting distances to the retracted vertex,
				// which is not on the shortest path from s to t.

				
				if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;

				} else if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;

				} else {

					vertex = null;

				}

			} else { // vertex is retracted

				// Also, set the distances to the deployed parent vertex
				// before setting the distances to the retracted parent vertex
				if (vertex.changingStateParent != null) {

					vertex.changingStateParent.omegaWeightOfTheLeastWeightPathBackwardFromT = sumWeight
							+ vertex.changingStateParent.changingStateEdge.visibilityUsedMinutes;

					vertex.changingStateParent.omegaCostOfTheLeastWeightPathBackwardFromT = sumCost
							+ vertex.changingStateParent.changingStateEdge.invertedNetEnergyGainKwh;
				}
				// /\ End of the setting distances to the retracted vertex,
				// which is not on the shortest path from s to t.
				
				if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;

				} else if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;

				} else {

					vertex = null;

				}

			}

			if (vertex != null) {

				vertex.omegaWeightOfTheLeastWeightPathBackwardFromT = sumWeight;
				vertex.omegaCostOfTheLeastWeightPathBackwardFromT = sumCost;

				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

				if (bPrintTracerCode) {
					// tracer code
					// D or R
					char dOrR = vertex.bDeployed ? 'D' : 'R';
					System.out.println("calculateShortestPathFromDestinationWeight" + dOrR + vertex.timestep);
				}

			}

			// Look for transition between deployed and retracted
			if (bPrintTransitions) {

				printTransitions(previousVertex, vertex);

			}
			previousVertex = vertex;

		}

		return (sumCost);

	}

	public void printShortestPathFromDestinationWeight() {

		int indexOfDestinationVertex = listRetractedVertex.size() - 1;
		Vertex vertex = listRetractedVertex.get(indexOfDestinationVertex);

		vertex.omegaWeightOfTheLeastWeightPathBackwardFromT = 0;
		vertex.omegaCostOfTheLeastWeightPathBackwardFromT = 0;

		// The least weight path will be the one that is always retracted when possible
		// Thus, to find the least weight and cost, simply follow the path that is as
		// retracted as possible.
		while (vertex != null) {

			// Take the retracted edge backwards if it exists
			if (vertex.bDeployed) {

				if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;

				} else if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;

				} else {

					vertex = null;

				}

			} else {

				if (vertex.keepingStateParent != null) {

					vertex = vertex.keepingStateParent;

				} else if (vertex.changingStateParent != null) {

					vertex = vertex.changingStateParent;

				} else {

					vertex = null;

				}

			}

			if (bPrintTracerCode) {
				// print path
				if (vertex != null) {

					// D or R
					char dOrR = vertex.bDeployed ? 'D' : 'R';
					System.out.println(dOrR + vertex.timestep);

				}
			}

		}

	}

	public OptimumAlgorithm(String localStation, HarvesterModel localHarvester, Workload localWorkload) {

		harvester = localHarvester;
		ws = localWorkload;

		Workload.WindspeedSample sample;

		/* The simulation starts retracted */
		int index_of_first_active_lifting_edge = 0;
		int timestep = 0;
		int prevMonth = -1;
		while ((sample = ws.getNextWindspeedSampleTesting()) != null) {

			listDeployedVertex.add(new Vertex());
			Vertex deployed = listDeployedVertex.get(timestep);
			deployed.timestep = timestep;
			deployed.bDeployed = true;
			deployed.windspeed = sample.windspeed_knots;
			// test

			/* The harvester is already deployed */

			deployed.localDateTime = sample.date;
			int currentMonth = deployed.localDateTime.getMonthValue();
			deployed.bReplenishment = (prevMonth != currentMonth);
			prevMonth = currentMonth;

			// If deployed vertex is in a restricted condition, then delete it at all linked
			// edges
			final int START_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR = 22;
			final int END_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR = 7;
			if (ws.bDuringNightlyVisibilityBan
					&& (deployed.localDateTime.getHour() >= START_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR
							|| deployed.localDateTime.getHour() < END_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR)) { // During
																											// active
																											// ban,
																											// thus,
																											// delete
																											// edges

				deployed.bIsolated = true;
				deployed.keepingStateEdge = null;
				deployed.changingStateEdge = null;
				// delete incoming edges
				if (timestep > 0) {
					Vertex previouslyDeployed = listDeployedVertex.get(timestep - 1);
					previouslyDeployed.keepingStateEdge = null; // delete deployed-to-deployed edge
				}
				if (timestep >= ws.iDeploymentTimeMinimumMinutes) {
					Vertex previouslyRetracted = listRetractedVertex.get(timestep - ws.iDeploymentTimeMinimumMinutes); // delete
																														// rising
																														// edge
					previouslyRetracted.changingStateEdge = null;
				}

			} else {

				/* The harvester is already deployed */
				deployed.keepingStateEdge.netEnergyGainKwh = (int) harvester.pc
						.windPowerKilowattsViaTable(sample.windspeed_knots, false);
				deployed.keepingStateEdge.timestepOfNextVertex = timestep + 1;
				deployed.keepingStateEdge.visibilityUsedMinutes = 1;

				/*
				 * Assumption: Because I am assuming that all harvesters only harvest when fully
				 * deployed, I check for maximum harvested energy only here.
				 */
				if (deployed.keepingStateEdge.netEnergyGainKwh > maximumNetEnergyGainKwh) {
					maximumNetEnergyGainKwh = deployed.keepingStateEdge.netEnergyGainKwh;
				}

				deployed.changingStateEdge.timestepOfNextVertex = timestep + ws.getRetractionTimeMinimumMinutes();
				deployed.changingStateEdge.netEnergyGainKwh = 0; // assume that it takes no power to retract because it
																	// uses gravity
				deployed.changingStateEdge.visibilityUsedMinutes = ws.getRetractionTimeMinimumMinutes();

			}

			/* The harvester is already stowed */
			listRetractedVertex.add(new Vertex());
			Vertex retracted = listRetractedVertex.get(timestep);
			retracted.windspeed = sample.windspeed_knots;
			retracted.timestep = timestep;
			retracted.bReplenishment = deployed.bReplenishment; // deployed and retracted are parallel in time
			int energyRequiredToLiftDuringThisTimestepKwh = harvester
					.getDeploymentEnergyUsedPerMinuteKwh(ws.getDeploymentTimeMinimumMinutes(), sample.windspeed_knots);

			/* retracting */
			retracted.changingStateEdge.timestepOfNextVertex = timestep + ws.getDeploymentTimeMinimumMinutes();
			retracted.changingStateEdge.visibilityUsedMinutes = ws.getDeploymentTimeMinimumMinutes();
			retracted.changingStateEdge.netEnergyGainKwh = 0 - energyRequiredToLiftDuringThisTimestepKwh;

			/* The harvester is already retracted */
			retracted.keepingStateEdge.timestepOfNextVertex = timestep + 1;
			retracted.keepingStateEdge.visibilityUsedMinutes = 0;
			retracted.keepingStateEdge.netEnergyGainKwh = 0;

			retracted.localDateTime = sample.date;

			/*
			 * cycle through all active "rising" edges except the current timestep to update
			 * energy used to lift the harvester
			 */
			/* This loop is required when the deployment time is more than one timestep */
			for (int active_power_lifting_index = index_of_first_active_lifting_edge; active_power_lifting_index < timestep; active_power_lifting_index++) {

				Vertex activeRetractedVertex = listRetractedVertex.get(active_power_lifting_index);
				if (activeRetractedVertex.changingStateEdge != null) {
					activeRetractedVertex.changingStateEdge.netEnergyGainKwh = activeRetractedVertex.changingStateEdge.netEnergyGainKwh
							- energyRequiredToLiftDuringThisTimestepKwh;
				}

			}

			/* update indices */
			if (timestep > ws.getDeploymentTimeMinimumMinutes()) {
				index_of_first_active_lifting_edge++;
			}
			timestep++;
		}

		// Because we are assuming that the harvester will start retracted,
		// we need to isolate all unreachable deployed nodes
		for (int i = 0; i < harvester.TIME_TO_DEPLOY_MINUTES; i++) {
			Vertex deployed = listDeployedVertex.get(i);
			deployed.changingStateEdge = null;
			deployed.keepingStateEdge = null;
		}

		// add the "off-graph" vertexes, which are needed because each sample is
		// represented by an edge
		listDeployedVertex.add(new Vertex());
		Vertex deployed = listDeployedVertex.get(timestep);
		deployed.bDeployed = true;
		deployed.timestep = timestep;
		deployed.changingStateEdge = null;
		deployed.keepingStateEdge = null;
		// trim its edges
		listRetractedVertex.add(new Vertex());
		Vertex retracted = listRetractedVertex.get(timestep);
		retracted.timestep = timestep;
		retracted.changingStateEdge = null;
		retracted.keepingStateEdge = null;

		// trim its edges

		// Set the shortest path cost for each edge
		for (Vertex dv : listDeployedVertex) {
			if (dv.changingStateEdge != null) {
				dv.changingStateEdge.invertedNetEnergyGainKwh = maximumNetEnergyGainKwh
						- dv.changingStateEdge.netEnergyGainKwh;
			}
			if (dv.keepingStateEdge != null) {
				dv.keepingStateEdge.invertedNetEnergyGainKwh = maximumNetEnergyGainKwh
						- dv.keepingStateEdge.netEnergyGainKwh;
			}
		}

		// Set the shortest path cost for each edge
		for (Vertex rv : listRetractedVertex) {
			if (rv.changingStateEdge != null) {
				rv.changingStateEdge.invertedNetEnergyGainKwh = maximumNetEnergyGainKwh
						- rv.changingStateEdge.netEnergyGainKwh;
			}
			if (rv.keepingStateEdge != null) {
				rv.keepingStateEdge.invertedNetEnergyGainKwh = maximumNetEnergyGainKwh
						- rv.keepingStateEdge.netEnergyGainKwh;
			}
		}

		// Trim off-graph any retracting edges.  Do not trim retracting edge to dummy destination
		int i = (listDeployedVertex.size() - harvester.TIME_TO_DEPLOY_MINUTES);
		if (i < 0)
			i = 0;
		for (; i < listDeployedVertex.size(); i++) {
			Vertex activeDeployedVertex = listDeployedVertex.get(i);
			activeDeployedVertex.changingStateEdge = null;
		}
		
		
		// Do not trim off-graph staying-deployed edge. We need the edge to represent
		// wind energy available
		// during the time represented by the edge

		// Trim any off-graph deploying edges
		i = (listRetractedVertex.size() - harvester.TIME_TO_DEPLOY_MINUTES);
		if (i < 0)
			i = 0;
		for (; i < listRetractedVertex.size(); i++) {
			Vertex activeRetractedVertex = listRetractedVertex.get(i);
			activeRetractedVertex.changingStateEdge = null;
		}

		// Do not trim off-graph staying-retracted edge

		// Add a "zero-distanced" vertex to act as a destination node since we do not
		// know which of the two
		// actual nodes will be the final node
		destinationVertex = new Vertex();
		destinationVertex.bDeployed = false;
		destinationVertex.changingStateParent = listDeployedVertex.get(listRetractedVertex.size() - 1);
		destinationVertex.changingStateEdge = null;
		destinationVertex.keepingStateParent = listRetractedVertex.get(listDeployedVertex.size() - 1);
		destinationVertex.keepingStateEdge = null;
		destinationVertex.zetaCostOfLeastCostPathBackwardFromT = 0;
		destinationVertex.zetaWeightOfLeastCostPathBackwardFromT = 0;
		destinationVertex.timestep = listRetractedVertex.size();

		// Attach destinationVertex to final real vertices
		Vertex retractedVertex = listRetractedVertex.get(listRetractedVertex.size() - 1);

		retractedVertex.keepingStateEdge = new ExitingEdge();
		retractedVertex.keepingStateEdge.timestepOfNextVertex = destinationVertex.timestep;
		retractedVertex.keepingStateEdge.invertedNetEnergyGainKwh = 0;
		retractedVertex.keepingStateEdge.netEnergyGainKwh = 0;
		retractedVertex.keepingStateEdge.visibilityUsedMinutes = 0;

		Vertex deployedVertex = listDeployedVertex.get(listDeployedVertex.size() - 1);

		deployedVertex.changingStateEdge = new ExitingEdge();
		deployedVertex.changingStateEdge.timestepOfNextVertex = destinationVertex.timestep;
		deployedVertex.changingStateEdge.invertedNetEnergyGainKwh = 0;
		deployedVertex.changingStateEdge.netEnergyGainKwh = 0;
		deployedVertex.changingStateEdge.visibilityUsedMinutes = 0;

		listRetractedVertex.add(destinationVertex);

		populateBackwardPointers();
	}

	// Populate backward pointers
	private void populateBackwardPointers() {

		for (Vertex vertex : listDeployedVertex) {
			// backward populate all vertices attached to the Deployed vertices
			if (vertex.keepingStateEdge != null) {
				Vertex keepingStateVertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);
				keepingStateVertex.keepingStateParent = vertex;
			}
			if (vertex.changingStateEdge != null) {
				Vertex changingStateVertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);
				changingStateVertex.changingStateParent = vertex;
			}

		}

		for (Vertex vertex : listRetractedVertex) {
			// backward populate all vertices attached to the Retracted vertices
			if (vertex.keepingStateEdge != null) {
				Vertex keepingStateVertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);
				keepingStateVertex.keepingStateParent = vertex;
			}
			if (vertex.changingStateEdge != null) {
				Vertex changingStateVertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);
				changingStateVertex.changingStateParent = vertex;
			}

		}

	}

	
	private void printGraphInDotLanguage1() {
		
		System.out.println("\n digraph A {");

		for (int i = 0; i < listDeployedVertex.size(); i++) {

			Vertex vertex = listDeployedVertex.get(i);
			if (vertex.keepingStateEdge != null) {
				System.out.print("D" + i + " -> ");
				System.out.print("D" + vertex.keepingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\"" 
						//+ vertex.keepingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.keepingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.keepingStateEdge.invertedNetEnergyGainKwh + " cost "
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

			if (vertex.changingStateEdge != null) {
				System.out.print("D" + i + " -> ");
				System.out.print("R" + vertex.changingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\""
					//	+ vertex.changingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.changingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.changingStateEdge.invertedNetEnergyGainKwh + " cost "
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

			vertex = listRetractedVertex.get(i);
			if (vertex.keepingStateEdge != null) {
				System.out.print("R" + i + " -> ");
				System.out.print("R" + vertex.keepingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\""
						// + vertex.keepingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.keepingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.keepingStateEdge.invertedNetEnergyGainKwh + " cost "
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");

			}

			if (vertex.changingStateEdge != null) {
				System.out.print("R" + i + " -> ");
				System.out.print("D" + vertex.changingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\"" 
						//+ vertex.changingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.changingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.changingStateEdge.invertedNetEnergyGainKwh + " cost "
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

		}

		System.out.println("}");

		System.out.println("\n");
	}

	
	
	
	private void printGraphInDotLanguage2() {
		
		System.out.println("\n digraph L {");

		for (int i = 0; i < listDeployedVertex.size(); i++) {

			Vertex vertex = listDeployedVertex.get(i);
			if (vertex.keepingStateEdge != null) {
				System.out.print("D" + i + " -> ");
				System.out.print("D" + vertex.keepingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\"" 
						//+ vertex.keepingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.keepingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.keepingStateEdge.invertedNetEnergyGainKwh + " cost "
						//+ vertex.costLabel + " is the cost label "
						//+ vertex.weightLabel + " is the weight label"
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

			if (vertex.changingStateEdge != null) {
				System.out.print("D" + i + " -> ");
				System.out.print("R" + vertex.changingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\""
					//	+ vertex.changingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.changingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.changingStateEdge.invertedNetEnergyGainKwh + " cost "
						//+ vertex.costLabel + " is the cost label "
						//+ vertex.weightLabel + " is the weight label"
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

			vertex = listRetractedVertex.get(i);
			if (vertex.keepingStateEdge != null) {
				System.out.print("R" + i + " -> ");
				System.out.print("R" + vertex.keepingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\""
						// + vertex.keepingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.keepingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.keepingStateEdge.invertedNetEnergyGainKwh + " cost "
						//+ vertex.costLabel + " is the cost label "
						//+ vertex.weightLabel + " is the weight label"
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");

			}

			if (vertex.changingStateEdge != null) {
				System.out.print("R" + i + " -> ");
				System.out.print("D" + vertex.changingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\"" 
						//+ vertex.changingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.changingStateEdge.visibilityUsedMinutes + " min. "
						+ vertex.changingStateEdge.invertedNetEnergyGainKwh + " cost "
						//+ vertex.costLabel + " is the cost label "
						//+ vertex.weightLabel + " is the weight label"
						// + vertex.localDateTime
						// " Settled:" + vertex.bSettled +
						// " CumulativeWt:" + vertex.zetaWeightOfLeastCostPathForwardFromS +
						// " Replenishment: " + vertex.bReplenishment +
						+ "\" ];");
			}

		}

		System.out.println("}");

		System.out.println("\n");
	}

	private void printGraphRisingEdge() {
		System.out.println("\n digraph B {");

		for (int i = 0; i < listDeployedVertex.size(); i++) {

			Vertex vertex = listRetractedVertex.get(i);

			if (vertex.changingStateEdge != null) {
				System.out.print("R" + i + " -> ");
				System.out.print("D" + vertex.changingStateEdge.timestepOfNextVertex);
				System.out.println("[ label=\"" 
						//+ vertex.changingStateEdge.netEnergyGainKwh + " kwh "
						+ vertex.changingStateEdge.visibilityUsedMinutes + " min. " + " Windspeed knots: "
						+ vertex.windspeed + "\" ];");
			}

		}

		System.out.println("}");

		System.out.println("\n");
	}

	// print path from end to beginning
	private void printGraphInDotLanguageOptimumPath() {

		System.out.println("\n digraph O {");
		String D_or_R_current = "D"; // assume final vertex is deployed
		Vertex vertex = listDeployedVertex.get(listDeployedVertex.size() - 1); // get deployed vertex
		while (vertex.zetaCostOfLeastCostPathForwardFromSParent != null) {
			// get next vertex backward
			String D_or_R_previous = D_or_R_current;
			D_or_R_current = vertex.zetaCostOfLeastCostPathForwardFromSParent.bDeployed ? "D" : "R";

			System.out.print(D_or_R_previous + vertex.timestep + " -> ");
			System.out.println(D_or_R_current + vertex.zetaCostOfLeastCostPathForwardFromSParent.timestep
					+ "[ label=\"" + "Weight-Limit Surpassed Forward: " + vertex.bZetaWeightLimitViolatedForwardFromS
					+ "\" ];");

			vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;
		}

		System.out.println("}");

		System.out.println("\n");
	}

	// print path from end to beginning
	// private void printGraphBackwards() {
	//
	// String D_or_R_current = "Warning:NotSet";
	//
	// System.out.println("\n digraph B {");
	//
	// //find the vertex in the shortest path
	// Vertex retractedVertex = listRetractedVertex.get(0); //get deployed vertex
	// Vertex deployedVertex = listDeployedVertex.get(0); //get deployed vertex
	// Vertex vertex = null;
	//
	// if (retractedVertex.zetaCostOfLeastCostPathBackwardFromTChild != null) {
	// D_or_R_current = "R"; // assume final vertex is deployed
	// vertex = retractedVertex;
	// }
	//
	// if (deployedVertex.zetaCostOfLeastCostPathBackwardFromTChild != null) {
	// D_or_R_current = "D"; // assume final vertex is deployed
	// vertex = deployedVertex;
	// }
	//
	// while (vertex.zetaCostOfLeastCostPathBackwardFromTChild != null) {
	// // get next vertex backward
	// String D_or_R_previous = D_or_R_current;
	// D_or_R_current = vertex.zetaCostOfLeastCostPathBackwardFromTChild.bDeployed ?
	// "D" : "R";
	//
	// System.out.print(D_or_R_previous + vertex.timestep + " -> ");
	// System.out.println(D_or_R_current +
	// vertex.zetaCostOfLeastCostPathBackwardFromTChild.timestep
	// + "[ label=\"" + "Weight-Limit Surpassed Backward: " +
	// vertex.bZetaWeightLimitViolatedBackwardFromT + "\" ];");
	//
	// vertex = vertex.zetaCostOfLeastCostPathBackwardFromTChild;
	// }
	//
	// System.out.println("}");
	//
	// System.out.println("\n");
	// }

	public void iterateThroughVertices() {

		for (Vertex v : listDeployedVertex) {
			System.out.print("Me:" + v.timestep);
			/*
			 * System.out.println("Parent:"+
			 * v.zetaCostOfLeastCostPathForwardFromSUpdating==null ? " null" :
			 * v.zetaCostOfLeastCostPathForwardFromSUpdating.timestep);
			 */
			if (v.zetaCostOfLeastCostPathForwardFromSUpdating == null) {
				System.out.println("Parent:null");
			} else {
				System.out.println("Parent:" + v.zetaCostOfLeastCostPathForwardFromSUpdating.timestep);
			}
		}

	}

	// print path from end to beginning
	private void printOptimalForwardsPathFromTtoStart() {

		String D_or_R_current = "Warning:NotSet";

		System.out.println("\n Optimal Path Forwards From End to S :");

		Vertex vertex = destinationVertex;

		while (vertex.zetaCostOfLeastCostPathForwardFromSUpdating != null) {
			vertex = vertex.zetaCostOfLeastCostPathForwardFromSUpdating;
			D_or_R_current = vertex.bDeployed ? "D" : "R";
			System.out.println(D_or_R_current + vertex.timestep);

		}

		System.out.println("\n");
	}

	// *****************
	// Defined on page 5
	// *****************
	long functionF(boolean bReplenishment, long u1, long u2) {

		long valueToReturn = 0;

		if (bReplenishment) {

			if (u1 > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

				// System.out.println("functionF returning Max. Value.");
				return (Long.MAX_VALUE);

			} else {

				// System.out.println("functionF returning u2: " + u2);
				return (u2);

			}

		} else {

			// System.out.println("functionF returning u1 + u2: "+ (u1 + u2) );
			return (u1 + u2);

		}

	}

/*	// *****************
	// Defined on page 5
	// *****************
	long functionFold(Vertex v) {

		long u1 = v.omegaWeightOfTheLeastWeightPathForwardFromS;
		long u2 = v.omegaWeightOfTheLeastWeightPathBackwardFromT;

		if (v.bReplenishment) {

			if (u1 > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {

				return (Long.MAX_VALUE);

			} else {

				return (u2);

			}

		} else {

			return (u1 + u2);

		}

	}
*/
	boolean loopAtOneDotThirteenCommonRoutine(Vertex v) {

		boolean bChangeMade = false;
/*		boolean functionFValue = (functionF(v.bReplenishment, v.omegaWeightOfTheLeastWeightPathForwardFromS,
				v.omegaWeightOfTheLeastWeightPathBackwardFromT) > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
		boolean bCostComparisonValue = (v.zetaCostOfLeastCostPathForwardFromS
				+ v.zetaCostOfLeastCostPathBackwardFromT >= zetaPlus); //changed >= to > // different from publication
*/
		long leftSideOfFunctionF = 0;
		boolean functionFValue = (leftSideOfFunctionF = functionF(v.bReplenishment, v.omegaWeightOfTheLeastWeightPathForwardFromS,
				v.omegaWeightOfTheLeastWeightPathBackwardFromT)) > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth;

		long leftSideOfCostComparison = 0;
		boolean bCostComparisonValue = (leftSideOfCostComparison = (v.zetaCostOfLeastCostPathForwardFromS
				+ v.zetaCostOfLeastCostPathBackwardFromT)) > zetaPlus;  //changed >= to > // different from publication
		
		if (functionFValue) {
			
			System.out.println("v.omegaWeightOfTheLeastWeightPathForwardFromS:" + v.omegaWeightOfTheLeastWeightPathForwardFromS);
			System.out.println("v.omegaWeightOfTheLeastWeightPathBackwardFromT:" + v.omegaWeightOfTheLeastWeightPathBackwardFromT);
			System.out.println("leftSideOfFunctionF:" + leftSideOfFunctionF);			
			System.out.println("ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth:" +
					ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
		}
		
		if (functionFValue || bCostComparisonValue) {
			// remove links to vertex

			if (v.changingStateParent != null) {

				v.changingStateParent.changingStateEdge = null;
				v.changingStateParent = null;
				bChangeMade = true;

			}
			if (v.keepingStateParent != null) {

				v.keepingStateParent.keepingStateEdge = null;
				v.keepingStateParent = null;
				bChangeMade = true;

			}
			if (v.changingStateEdge != null) {

				if (v.bDeployed) {
					
					Vertex child = listRetractedVertex.get(v.changingStateEdge.timestepOfNextVertex);
					child.changingStateParent = null;
					
				} else {
					
					Vertex child = listDeployedVertex.get(v.changingStateEdge.timestepOfNextVertex);
					child.changingStateParent = null;
					
				}					

			}
			if (v.keepingStateEdge != null) {

				if (v.bDeployed) {
					
					Vertex child = listDeployedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
					child.keepingStateParent = null;
					
				} else {
					
					Vertex child = listRetractedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
					child.keepingStateParent = null;
					
				}					

			}
			char dOrR = v.bDeployed ? 'D' : 'R';
			System.out.println("loopAtOneDotThirteenCommonRoutine: Removing links to vertex:" + dOrR + v.timestep
					+ 
					" functionFValue: " + functionFValue +
					" bCostComparisonValue: " + bCostComparisonValue);
			
			// debug
			if ((v.timestep == 3) && (!v.bDeployed)) {
				
				System.out.println("Breakpoint");
				
			}
			

		}

		if (bChangeMade)
			System.out.println("loopAtOneDotThirteenCommonRoutine: Print change made.");

		return (bChangeMade);
	}

	boolean loopAtOneDotSeventeenCommonRoutine(Vertex v) {

		boolean bChangeMade = false;

		// Examine changingStateEdge (if not null) and
		// then, examine keepingStateEdge (if not null)

		if (v.changingStateEdge != null) {

			ExitingEdge e = v.changingStateEdge;
			bChangeMade = loopAtOneDotSeventeenCommonRoutineEdgeSpecific(v, e, true) || 
					bChangeMade;
		}

		if (v.keepingStateEdge != null) {

			ExitingEdge e = v.keepingStateEdge;
			bChangeMade = loopAtOneDotSeventeenCommonRoutineEdgeSpecific(v, e, false) || bChangeMade;
		}

		if (bChangeMade) {

			// tracer code
			// D or R
			if (bPrintTracerCode) {
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println("loopAtOneDotSeventeenCommonRoutine: Change made:" +
						dOrR + v.timestep);
			}
			
		}

		return (bChangeMade);
	}

	boolean loopAtOneDotSeventeenCommonRoutineEdgeSpecific(Vertex v, ExitingEdge e,
			boolean bChangingStateEdge) {

//		if (v.timestep == 1 && v.bDeployed) {
//			
//			System.out.println("Hey, I am at D1.");
//			
//		}
		
		// Get child vertex
		Vertex child = null;
		if (bChangingStateEdge) {
			int indexOfNextVertex = v.changingStateEdge.timestepOfNextVertex;
			child = (v.bDeployed) ? listRetractedVertex.get(indexOfNextVertex)
					: listDeployedVertex.get(indexOfNextVertex);
		} else {
			int indexOfNextVertex = v.keepingStateEdge.timestepOfNextVertex;
			child = (v.bDeployed) ? listDeployedVertex.get(indexOfNextVertex)
					: listRetractedVertex.get(indexOfNextVertex);
		}

		boolean bChangeMade = false;

		// Line 1.18 and 1.19
		boolean bFunctionFValue = (functionF(v.bReplenishment, v.omegaWeightOfTheLeastWeightPathForwardFromS,
				e.visibilityUsedMinutes
						+ child.omegaWeightOfTheLeastWeightPathBackwardFromT) > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
		/* boolean bCostComparisonValue = (v.zetaCostOfLeastCostPathForwardFromS + e.invertedNetEnergyGainKwh
				+ child.zetaCostOfLeastCostPathBackwardFromT >= zetaPlus); ////changed >= to > // different from publication
		*/
		long leftSideOfCostComparison = 0;
		boolean bCostComparisonValue = (leftSideOfCostComparison =(v.zetaCostOfLeastCostPathForwardFromS + e.invertedNetEnergyGainKwh
				+ child.zetaCostOfLeastCostPathBackwardFromT)) > zetaPlus; ////changed >= to > // different from publication

		
		if (bCostComparisonValue) {
			
			System.out.println("For " + (v.bDeployed? "D":"R") +  v.timestep + ":");
			System.out.println(" v.zetaCostOfLeastCostPathForwardFromS:" + v.zetaCostOfLeastCostPathForwardFromS);
			System.out.println(" e.invertedNetEnergyGainKwh:" + e.invertedNetEnergyGainKwh);
			System.out.println(" child.zetaCostOfLeastCostPathBackwardFromT:" + child.zetaCostOfLeastCostPathBackwardFromT);
			System.out.println(" Tot:" + leftSideOfCostComparison);
			System.out.println(" zetaPlus:" + zetaPlus);
		}
		
		if (bFunctionFValue || bCostComparisonValue) {
			// remove edge by setting vertex's pointer to edge to null and
			// by removing links in child node to parent
			if (bChangingStateEdge) {

				child.changingStateParent = null;

				v.changingStateEdge = null;

				// D or R
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println("loopAtOneDotSeventeenCommonRoutineEdgeSpecific:");
				System.out.println("Line 1.18 and 1.19:Removing changing state edge leaving:" + dOrR + v.timestep
						+ " FunctionFValue:" + bFunctionFValue + " CostComparisonValue:" + bCostComparisonValue);

			} else {

				child.keepingStateParent = null;

				v.keepingStateEdge = null;

				// D or R
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println("loopAtOneDotSeventeenCommonRoutineEdgeSpecific:");
				System.out.println("Line 1.18 and 1.19:Removing keeping state edge leaving:" + dOrR + v.timestep
						+ " FunctionFValue:" + bFunctionFValue + " CostComparisonValue:" + bCostComparisonValue);

			}
			e = null;
			bChangeMade = true;
			char dOrR = v.bDeployed ? 'D' : 'R';

		} else if ((functionF(v.bReplenishment, v.zetaWeightOfLeastCostPathForwardFromS, e.visibilityUsedMinutes
				+ child.zetaWeightOfLeastCostPathBackwardFromT) <= ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
				&& (v.bZetaWeightLimitViolatedForwardFromS == false)
				&& (child.bZetaWeightLimitViolatedBackwardFromT == false)) {

			// Line 1.21
			long zetaPlusPrevious = zetaPlus;
			zetaPlus = Math.min(zetaPlus, v.zetaCostOfLeastCostPathForwardFromS + e.invertedNetEnergyGainKwh
					+ child.zetaCostOfLeastCostPathBackwardFromT);

			if (zetaPlusPrevious != zetaPlus) {  //Note: this test is not in the originally published algorithm
				bChangeMade = true;
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println(
					"Line 1.21: Updated zeta+.: cost of least cost through " 
					+ dOrR + v.timestep +
					"->" +
					(child.bDeployed? 'D' : 'R') + child.timestep +
					":z+:" + zetaPlusPrevious + " to " + zetaPlus);

					mostRecentLineWhereZetaPlusWasUpdated = 21;
					mostRecentLineWhereZetaPlusWasUpdatedParent = v;
					mostRecentLineWhereZetaPlusWasUpdatedChild = child;
			}

		} else if ((functionF(v.bReplenishment, // line 1.22 start
				v.zetaWeightOfLeastCostPathForwardFromS,
				e.visibilityUsedMinutes
						+ child.zetaWeightOfLeastCostPathBackwardFromT) <= ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
				&& (v.bZetaWeightLimitViolatedForwardFromS == false)) { // line 1.22 end
			// line 1.23
			long zetaPlusPrevious = zetaPlus;
			zetaPlus = Math.min(zetaPlus, v.zetaCostOfLeastCostPathForwardFromS + e.invertedNetEnergyGainKwh
					+ child.omegaCostOfTheLeastWeightPathBackwardFromT);

			if (!v.bDeployed && v.timestep == 1) {
				
				System.out.println("Temp");
				
			}
			
			if (zetaPlusPrevious != zetaPlus) {  //Note: this test is not in the originally published algorithm
				bChangeMade = true;
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println(
					"Line 1.23: Updated zeta+.: Cost of least cost, then cost of least weight through " + dOrR + v.timestep +
					"->" +
					(child.bDeployed? 'D' : 'R') + child.timestep +
					":z+:" + zetaPlusPrevious + " to " + zetaPlus);
					mostRecentLineWhereZetaPlusWasUpdated = 23;
					mostRecentLineWhereZetaPlusWasUpdatedParent = v;
					mostRecentLineWhereZetaPlusWasUpdatedChild = child;

			}

		} else if ((functionF(v.bReplenishment, // line 1.24 start
				v.omegaWeightOfTheLeastWeightPathForwardFromS,
				e.visibilityUsedMinutes
						+ child.zetaWeightOfLeastCostPathBackwardFromT) <= ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth)
				&& (child.bZetaWeightLimitViolatedBackwardFromT == false)) { // line 1.25
			long zetaPlusPrevious = zetaPlus;
			zetaPlus = Math.min(zetaPlus, v.omegaCostOfTheLeastWeightPathForwardFromS + e.invertedNetEnergyGainKwh
					+ child.zetaCostOfLeastCostPathBackwardFromT);

			if (zetaPlusPrevious != zetaPlus) {  //Note: this test is not in the originally published algorithm
				bChangeMade = true;
				char dOrR = v.bDeployed ? 'D' : 'R';
				System.out.println(
					"Line 1.25: Updated zeta+.: cost of least weight, then cost of least cost through " +
					dOrR + v.timestep +
					"->" +
					(child.bDeployed? 'D' : 'R') + child.timestep +
					":z+:" + zetaPlusPrevious + " to " + zetaPlus);
					mostRecentLineWhereZetaPlusWasUpdated = 25;
					mostRecentLineWhereZetaPlusWasUpdatedParent = v;
					mostRecentLineWhereZetaPlusWasUpdatedChild = child;

			}

		} // line 1.26 ends an if... clause

		return (bChangeMade);
	}

	boolean loopAtOneDotThirteen() {

		boolean changeMade = false;

		// iterate through all vertices
		for (Vertex v : listRetractedVertex) {

			changeMade = loopAtOneDotThirteenCommonRoutine(v) || changeMade; // if changeMade, do not
																						// short-circuit

		}

		for (Vertex v : listDeployedVertex) {

			changeMade = loopAtOneDotThirteenCommonRoutine(v) || changeMade; // if changeMade, do not
																						// short-circuit

		}

		return (changeMade);
	}

	boolean loopAtOneDotSeventeen() {

		boolean changeMade = false;

		// iterate through all vertices
		for (Vertex v : listRetractedVertex) {

			changeMade = loopAtOneDotSeventeenCommonRoutine(v) || changeMade; // if changeMade, do not
																						// short-circuit

		}

		for (Vertex v : listDeployedVertex) {

			changeMade = loopAtOneDotSeventeenCommonRoutine(v) || changeMade; // if changeMade, do not
																						// short-circuit

		}

		return (changeMade);
	}

	// print path from end to beginning
	private void printOptimalBackwardsPathFromStoEnd() {

		String D_or_R_current = "Warning:NotSet";

		System.out.println("\n Optimal Path Backwards From S to End :");

		// find the vertex in the shortest path
		// try
		Vertex vertex = listRetractedVertex.get(0); // get deployed vertex

		while (vertex.zetaCostOfLeastCostPathBackwardFromTUpdating != null) {
			D_or_R_current = vertex.bDeployed ? "D" : "R";
			System.out.println(D_or_R_current + vertex.timestep); // assume final vertex is deployed
			vertex = vertex.zetaCostOfLeastCostPathBackwardFromTUpdating;
		}

		System.out.println("\n");
	}

	/* populate the entire chart */
	/*
	 * Given the workload (i.e., restrictions and harvester model, what is the best)
	 */
	
	void printPathCreatedByAlgorithm2() {
		

		System.out.println("Path created by algorithm 2");
		// Find its least cost vertex
		Comparator<Vertex.Label> labelComparator;
		labelComparator = new ComparatorCostThenWeight();
		destinationVertex.listLabel.sort(labelComparator);
		
//		System.out.println("The sorted order is ");
		for (Vertex.Label label : destinationVertex.listLabel) {
			
			System.out.println("Class: " + label.getClass().getName());
			System.out.println(label.costLabel);
			
		}
		Vertex.Label label = destinationVertex.listLabel.get(0);
		System.out.println("The cost and weight of the label I am getting is " + 
				label.costLabel + " ," + label.weightLabel);
		System.out.println((destinationVertex.bDeployed ? "D" : "R") + destinationVertex.timestep);

		
		while (label.parentLabel != null) {

			label = label.parentLabel;
			System.out.println((label.id.bDeployed ? "D" : "R") 
					+ label.id.timestep);
			
		}


	}
	
	public void findOptimumPath() {
		
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
		
		System.out.println("Start to preprocess:" + sdf.format(cal.getTime()));
		boolean bOptimumFound = findOptimumPathPreprocess();
		System.out.println("Completed Preprocessing:" + sdf.format(cal.getTime()));
		
		if (!bOptimumFound) {
			
			findOptimumPathTreatLabels();
			System.out.println("Completed Label Treatment.");
			// Print vertices that last updated the labels
			printPathCreatedByAlgorithm2();
			//printGraphInDotLanguage2();
			
		}
		
	}
	
	
	private boolean findOptimumPathPreprocess() {

		//printGraphInDotLanguage1();
		
		System.out.println("zetaPlus is starting at value " + zetaPlus);
		// start while loop here //TODO
		boolean bFirstIterationOrChangesMade = true;
		boolean bOptimumPathFound = false;
		boolean bPathInfeasible = false; // path now infeasible
		int iteration = 0;
		while (bFirstIterationOrChangesMade) { // line 1.2
			bFirstIterationOrChangesMade = false;
			// ~Line 1.3
			System.out.print(".");
			bOptimumPathFound = calculateShortestPathFromSourceCostDijkstra(); // Algorithm steps 1.3 and 1.4
			// ~Line 1.4
			System.out.print(".");
			if (bOptimumPathFound) {

				System.out.println("Optimum found: Line 1.4");
				printGraphInDotLanguageOptimumPath();
				break;

			}
			// ~Line 1.5
			System.out.print(".");
			bOptimumPathFound = calculateShortestPathFromDestinationCostDijkstra();
			// ~Line 1.6
			System.out.print(".");
			if (bOptimumPathFound) {

				System.out.println("Optimum found: Line 1.6");
				//calculateShortestPathFromDestinationCost(true);
				printOptimumCostForward(listRetractedVertex.get(0));
				break;

			}

			// if (true) break; //troubleshooting

			// Line 1.7
			System.out.print(".");
			long omegaWeightFromSourceToTarget = 
					calculateShortestPathFromSourceWeight(listRetractedVertex.get(0), false);
			// Line 1.8
			System.out.print(".");
			if (omegaWeightFromSourceToTarget > ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
				System.out.println("Path is infeasible");
				bPathInfeasible = true;
				break;
			}
			// Line 1.9
			System.out.print(".");
			long zetaPlusPrevious = zetaPlus;
			zetaPlus = Math.min(zetaPlus, destinationVertex.omegaCostOfTheLeastWeightPathForwardFromS);
			if (zetaPlusPrevious != zetaPlus) {
				bFirstIterationOrChangesMade = true;
				System.out.println("Line 1.9: Updated zeta+.:" + ":z+:" + zetaPlusPrevious + " to " + zetaPlus);
				mostRecentLineWhereZetaPlusWasUpdated = 9;
			}
			// Line 1.10
			System.out.print(".");
			long omegaCostFromSourceToTarget = 
					calculateShortestPathFromDestinationWeight(listRetractedVertex.get(listRetractedVertex.size() - 1), false);
			// Line 1.11
			System.out.print(".");
			zetaPlusPrevious = zetaPlus;
			zetaPlus = Math.min(zetaPlus, omegaCostFromSourceToTarget);
			if (zetaPlusPrevious != zetaPlus) {
				bFirstIterationOrChangesMade = true;
				System.out.println("Line 1.11: Updated zeta+.:" + ":z+:" + zetaPlusPrevious + " to " + zetaPlus);
				mostRecentLineWhereZetaPlusWasUpdated = 11;
				// print optimum path if optimum found
				if (zetaPlus <= destinationVertex.zetaCostOfLeastCostPathForwardFromS) { // Zeta_{cs,t} is cost of least cost path

					System.out.println("Optimum found: Line 1.12 (omegaCostFromSourceToTarget is less than zetaPlus)");
					//printOmegaCostFromSourceToTarget
					calculateShortestPathFromDestinationWeight(listRetractedVertex.get(listRetractedVertex.size() - 1), true);
					bOptimumPathFound = true;
					break;

				}
				
				
			}
			// Line 1.12
			System.out.print(".");
			if (zetaPlus <= destinationVertex.zetaCostOfLeastCostPathForwardFromS) { // Zeta_{cs,t} is cost of least cost path
																						// from s to t

				System.out.println("Optimum found: Line 1.12");
				
				if (zetaPlus == destinationVertex.zetaCostOfLeastCostPathForwardFromS) {
					
					System.out.println("zetaPlus equals destinationVertex.zetaCostOfLeastCostPathForwardFromS:" +
							zetaPlus + " equals " + destinationVertex.zetaCostOfLeastCostPathForwardFromS);
					System.out.println("Look for most recent change in zetaPlus to determine weight-constrained shortest path");
					//printOptimumCostForward();
					//calculateShortestPathFromDestinationWeight(true);
					
				}
				
				//printGraphInDotLanguageOptimumPath();
				bOptimumPathFound = true;
				break;

			}

			// Line 1.13 to 1.16
			System.out.print(".");
			bFirstIterationOrChangesMade = loopAtOneDotThirteen() || bFirstIterationOrChangesMade; // be careful
																											// to not

			// Line 1.17 to 1.27
			System.out.print(".");
			bFirstIterationOrChangesMade = loopAtOneDotSeventeen() || bFirstIterationOrChangesMade; // be
																											// careful

			if (iteration == 1) {
				
				System.out.println("Pause 2");
				
			}
			
			if (iteration++ > 100) {

				System.out.println("Iteration limit reached: " + bFirstIterationOrChangesMade);
				break;

			}
			System.out.println("Iteration: " + iteration);

		} // end while loop // Line 1.28

		//calculateShortestPathFromSourceCost(true); //debugging // be sure to remove this line
		
		
		//calculateShortestPathFromDestinationWeight(true); //debugging // be sure to remove this line
		
		
		if (bOptimumPathFound) { // to do
			
			System.out.println("Optimum found.");
			
			// Print
			switch (mostRecentLineWhereZetaPlusWasUpdated) {
			
				case 9:
					
					break;
				case 11:
					
					break;
				case 21:
					printOptimumCostBackward(mostRecentLineWhereZetaPlusWasUpdatedParent);
					printOptimumCostForward(mostRecentLineWhereZetaPlusWasUpdatedChild);
					break;
				case 23:
					printOptimumCostBackward(mostRecentLineWhereZetaPlusWasUpdatedParent);
					// print least cost forward - weight
					calculateShortestPathFromSourceWeight(mostRecentLineWhereZetaPlusWasUpdatedChild, true);
					break;
				case 25:
					// print least cost backward - weight
					calculateShortestPathFromDestinationWeight(mostRecentLineWhereZetaPlusWasUpdatedParent, true);
					printOptimumCostForward(mostRecentLineWhereZetaPlusWasUpdatedChild);					
					break;
				default:
					System.out.println("Default of case statement");
					break;
			
			}
			
			
			
		}

		if (bPathInfeasible)
			System.out.println("Path infeasible.");

		//printGraphInDotLanguage1();

		// printGraphRisingEdge();
		// printGraphInDotLanguageOptimumPath();
		// calculateShortestPathFromDestinationCost();
		// printGraphBackwards();
		// printOptimalBackwardsPath();
		// iterateThroughVertices();
		// printOptimalForwardsPathFromTtoStart();
		// printOptimalBackwardsPathFromStoEnd();

		// System.out.println("The harvestser must take at least " +
		// ws.iDeploymentTimeMinimumMinutes + " minutes to deploy and at least " +
		// ws.iRetractionTimeMinimumMinutes + " minutes to retract.");
		// Workload.WindspeedSample sample;
		// System.out.println("Starting the test here.");
		//
		// System.out.println("The number of edges in the edge deployed list is " +
		// listDeployedVertex.size());
		// System.out.println("The number of edges in the edge retracted list is " +
		// listRetractedVertex.size());
		// System.out.println("Find the optimum path.");

		// for (Vertex Vertex : listDeployedVertex) {
		//
		// System.out.println("Staying deployed next vertex: " +
		// Vertex.keepingStateEdge.timestepOfNextVertex);
		// System.out.println("Deploy used visibility minutes: " +
		// Vertex.keepingStateEdge.visibilityUsedMinutes);
		// System.out.println("Deploy net energy gain kwh: " +
		// Vertex.keepingStateEdge.netEnergyGainKwh);
		// System.out.println("Deploy Shortest Path cost is: " +
		// Vertex.keepingStateEdge.invertedNetEnergyGainKwh);
		//
		// if (Vertex.changingStateEdge != null) {
		// System.out.println("Retracting next vertex: " +
		// Vertex.changingStateEdge.timestepOfNextVertex);
		// System.out.println("Retracting used minutes: " +
		// Vertex.changingStateEdge.visibilityUsedMinutes);
		// System.out.println("Retracting net energy gain kwh: " +
		// Vertex.changingStateEdge.netEnergyGainKwh);
		// System.out.println("Retractomg Shortest Path cost is: " +
		// Vertex.changingStateEdge.invertedNetEnergyGainKwh);
		//
		//
		//
		// } else {
		// System.out.println("The retracting edge is null");
		// }
		// }

		// System.out.println("The retracted vertex data:");
		// for (Vertex retractedVertex : listRetractedVertex) {
		//
		// System.out.println("Retracted next vertex: " +
		// retractedVertex.keepingStateEdge.timestepOfNextVertex);
		// System.out.println("Retracted used visibility minutes: " +
		// retractedVertex.keepingStateEdge.visibilityUsedMinutes);
		// System.out.println("Retracted net energy gain kwh: " +
		// retractedVertex.keepingStateEdge.netEnergyGainKwh);
		// System.out.println("Retracted Shortest Path cost is: " +
		// retractedVertex.keepingStateEdge.invertedNetEnergyGainKwh);
		//
		// if (retractedVertex.changingStateEdge != null) {
		// System.out.println("Deploying next vertex: " +
		// retractedVertex.changingStateEdge.timestepOfNextVertex);
		// System.out.println("Deploying used minutes: " +
		// retractedVertex.changingStateEdge.visibilityUsedMinutes);
		// System.out.println("Deploying net energy gain kwh: " +
		// retractedVertex.changingStateEdge.netEnergyGainKwh);
		// System.out.println("Deploying Shortest Path cost is: " +
		// retractedVertex.changingStateEdge.invertedNetEnergyGainKwh);
		// } else {
		// System.out.println("The deploying edge is null");
		// }
		// }

		/*
		 * "1.3..." [see paper] ;
		 */

		return (bOptimumPathFound);
		
	}


	private void findOptimumPathTreatLabelsAlgorithmCommon(Vertex v, Vertex.Label label, ExitingEdge e, Vertex child) {

			long w_tilde = 0;
			
			// Line 2.2
			if (v.bReplenishment) {
				
				w_tilde = e.visibilityUsedMinutes;
				
			} else {
				
				w_tilde = label.weightLabel + e.visibilityUsedMinutes;
				
			}
			// Line 2.3
			long candidateCost = label.costLabel + e.invertedNetEnergyGainKwh;
			if (
					(w_tilde + child.omegaWeightOfTheLeastWeightPathBackwardFromT <= ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth) 
						&&
						(candidateCost + child.zetaCostOfLeastCostPathBackwardFromT <= zetaPlus) // Paper uses < instead of <=
				) {
				
				// Check for any other labels on the child node that dominate the candidate label.
					
					// iterate through all of the child's labels and delete dominated labels
					for (int i = (child.listLabel.size()-1); i>-1; i--) {
						
						Vertex.Label childLabel = child.listLabel.get(i);
						// remove all labels that the candidate label dominates
						if (
								((candidateCost < childLabel.costLabel) && (w_tilde <= childLabel.weightLabel)) 
								||
								((candidateCost <= childLabel.costLabel) && (w_tilde < childLabel.weightLabel))
								)
							{

								child.listLabel.remove(i);

							}
					}
					
					// if the candidate is not dominated by any labels in Gamma_child, then
					//  add the candidate to Gamma_child and to SE
					boolean bCandidateLabelIsDominatedByOneOrMoreLabels = false;
					for (Vertex.Label childLabel : child.listLabel) {
						
						if (
								((childLabel.costLabel < candidateCost) && (childLabel.weightLabel <= w_tilde)) 
								||
								((childLabel.costLabel <= candidateCost) && (childLabel.weightLabel < w_tilde))
								)
							{

								bCandidateLabelIsDominatedByOneOrMoreLabels = true;
								break;

							}
					}
					if (!bCandidateLabelIsDominatedByOneOrMoreLabels) {
						
						Vertex.Label nondominatedLabel = child.new Label(candidateCost, w_tilde, label, child);
						child.listLabel.add(nondominatedLabel);
						queueSE.add(nondominatedLabel);
						
				}
			}
				
				




		
		
			

	}
	
	private void findOptimumPathTreatLabelsAlgorithm(Vertex.Label label) {

		Vertex v = label.id;
		// Determine which of v's labels is/are best for v's child or children

		// Line 2.1 - visit all edges leaving the vertex v
		
			if (v.changingStateEdge != null) {
				
				// get child vertex
				int timestepOfNextVertex = v.changingStateEdge.timestepOfNextVertex;
				Vertex child = v.bDeployed? listRetractedVertex.get(timestepOfNextVertex) :
					listDeployedVertex.get(timestepOfNextVertex);
				findOptimumPathTreatLabelsAlgorithmCommon(v, label, v.changingStateEdge, child);
			}
			
			if (v.keepingStateEdge != null) {
				
				// get child vertex
				int timestepOfNextVertex = v.keepingStateEdge.timestepOfNextVertex;
				Vertex child = v.bDeployed? listDeployedVertex.get(timestepOfNextVertex) :
					listRetractedVertex.get(timestepOfNextVertex);
				findOptimumPathTreatLabelsAlgorithmCommon(v, label, v.keepingStateEdge, child);
				
			}
		
		}
		

	
	private void findOptimumPathTreatLabels() {
		
		// Initialize
		Vertex startingVertex = listRetractedVertex.get(0);
		
		Vertex.Label startingLabel = 
				startingVertex.new Label((long) 0, (long) 0, null, startingVertex);
		startingVertex.listLabel.add(startingLabel); // Add to Gamma
		queueSE.add(startingLabel); // Add to SE
		
		startingVertex = null;
				
		// Iterate through labels until no scan-eligible labels remain
		while (!queueSE.isEmpty()) {
			
			Vertex.Label currentLabel = queueSE.remove();

/* Was commented out */
			System.out.println("The vertex associated with the current label is " + 
					currentLabel.id.bDeployed + 
					currentLabel.id.timestep);
			System.out.println("This vertex has a label list size of " + currentLabel.id.listLabel.size());
			System.out.println("The queueSE size is " + 
					queueSE.size());
			// What are the labels
			for (Vertex.Label label : currentLabel.id.listLabel) {
				
				System.out.println("\t cost:" + label.costLabel + " weight:" + label.weightLabel);
						
		}
			
			findOptimumPathTreatLabelsAlgorithm(currentLabel);
		
		}
		
		
	}

}


