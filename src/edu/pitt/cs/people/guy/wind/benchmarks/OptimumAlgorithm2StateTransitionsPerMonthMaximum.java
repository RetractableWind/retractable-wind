package edu.pitt.cs.people.guy.wind.benchmarks;

import java.util.ArrayList;

import java.util.HashSet;
//import java.util.Set;
import java.time.LocalDateTime;
import java.time.Month;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.LinkedList;

import edu.pitt.cs.people.guy.wind.benchmarks.OptimumAlgorithm.ComparatorZetaCostBackward;
import edu.pitt.cs.people.guy.wind.benchmarks.OptimumAlgorithm.ComparatorZetaCostForward;
import edu.pitt.cs.people.guy.wind.benchmarks.OptimumAlgorithm.Vertex;
import edu.pitt.cs.people.guy.wind.benchmarks.OptimumAlgorithm2StateTransitionsPerMonthMaximum.Vertex.Label;
import edu.pitt.cs.people.guy.wind.benchmarks.RetractableHarvesterBenchmarks.*;

/* */
/* Deploy or retract must be called every minute */
class OptimumAlgorithm2StateTransitionsPerMonthMaximum {

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
	
	final int NUM_MONTHS_IN_TESTING_DATA = (9 * 12)+1; // Five years: 2004-08 inclusive - add 1 month for code in buildAcyclicDigraphOfMonthlyStatistics()
	MonthStatistics[] arrayMonthStatistics = new MonthStatistics[NUM_MONTHS_IN_TESTING_DATA]; // create "pointers"
	//ArrayList<MonthStatistics> arrayMonthStatistics = new ArrayList<MonthStatistics>();
	
	MonthlyVertex  sourceMonthlyVertex;	
	MonthlyVertex  destinationMonthlyVertex;
	 
	enum Cases
    {
		ONE_RISE_ONE_FALL, ONE_RISE, ONE_FALL, BIFURCATED, STAY_RETRACTED;
    }
	
	
	class MonthStatistics {
				
		int month;
		
		Vertex firstDeployedVertex = null;   // deployed vertex having the first timestamp of the month;
		Vertex firstRetractedVertex = null; // retracted vertex having the first timestamp of the month;

		Vertex lastDeployedVertex = null;   // deployed vertex having the last timestamp of the month;
		Vertex lastRetractedVertex = null; // retracted vertex having the last timestamp of the month;
		
		//long arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].costOfBestDeploymentOneFall = Long.MAX_VALUE; // best = least cost
		
		int timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall = -1;
		
		//long arrayMonthlyVertex[ONE_FALL].costOfBestDeployment = Long.MAX_VALUE; // best = least cost
		
		//long arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].costOfBestDeployment = Long.MAX_VALUE; // best = least cost

		//long arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment = Long.MAX_VALUE;
		
		int timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise = -1; // Best is the best first + second deployment
		int timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise = -1;
		

		MonthlyVertex[] arrayMonthlyVertex = new MonthlyVertex[Cases.values().length]; // Create spaces for four (e.g.) pointers.  The objects will
																   					   //  be created in the constructor
		
		// Constructor
		MonthStatistics() {
			
			for (int i = 0; i < Cases.values().length; i++) {
				
				arrayMonthlyVertex[i] = new MonthlyVertex();
				arrayMonthlyVertex[i].caseOfVertex = Cases.values()[i];
			}
						
			
		}
				
						
	}
	
	
	class MonthlyVertex {
		
		MonthlyVertex nextVertexRepresentingTwoTransitions; // Harvester rises and falls (or vice-a-versa)
		MonthlyVertex nextVertexRepresentingOneTransition;  // Harvester only rises or only falls
		MonthlyVertex nextVertexRepresentingStayingRetracted;
		long costOfBestDeployment = Long.MAX_VALUE;
		long zetaCostOfLeastCostPathForwardFromS = Long.MAX_VALUE;
		boolean bSettled = false; // for use in the shortest path algorithm
		MonthlyVertex zetaCostOfLeastCostPathForwardFromSParent = null;
		MonthlyVertex zetaCostOfLeastCostPathForwardFromSChild = null; //populate after best path is found
		
		boolean bEdgeOnePrinted = false; // for use in the printing function
		boolean bEdgeTwoPrinted = false; // for use in the printing function
		int iBestTimestepToDeploy = -1; // produce training data
		int iBestTimestepToRetract = -1; // produce training data
		Cases caseOfVertex;
		
	}

	

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
	class ComparatorMonthlyCostForward implements Comparator<MonthlyVertex > {

		public int compare(MonthlyVertex  x, MonthlyVertex  y) {
			if (x.costOfBestDeployment < y.costOfBestDeployment) {
				return -1;
			} else if (x.costOfBestDeployment > y.costOfBestDeployment) {
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

	
	
private void calculateShortestPathFromSourceCostDijkstraMonthlyVertexWithStayingRetracted(MonthlyVertex  vertex) {
	    		
		// Distance from the source to the source is 0
		vertex.zetaCostOfLeastCostPathForwardFromS = 0;
		destinationMonthlyVertex.costOfBestDeployment = 0;

	    // Set<Vertex> settledNodes = new HashSet<Vertex>();
	    // Set<Vertex> unsettledNodes = new HashSet<Vertex>();
	 
	    /* "We add the startNode to the unsettled nodes set." */
	    // unsettledNodes.add(retracted);
	    /* "Choose an evaluation node from the unsettled nodes set, 
	     * the evaluation node should be the one with the lowest distance from the source." */
	    // The lowest distance from node will be the deployed node if it is available
	    
		// Visit
		
		final int PRIORITY_QUEUE_CAPACITY = 7;
		Comparator<MonthlyVertex> queueComparator = new ComparatorMonthlyCostForward();
		PriorityQueue<MonthlyVertex > priorityQueue =
				new PriorityQueue<MonthlyVertex >(PRIORITY_QUEUE_CAPACITY, queueComparator);

		
		while (vertex != null) {

			vertex.bSettled = true; // Vertex is settled; Thus, set weight flag
			System.out.println("The settled cost of vertex " + vertex + " is " +
					vertex.zetaCostOfLeastCostPathForwardFromS);
			if (62367608 == vertex.zetaCostOfLeastCostPathForwardFromS) {
				
				System.out.println("pause");
				
			}
			
			if (vertex.nextVertexRepresentingOneTransition != null) {
														
				long preliminaryDistance = vertex.nextVertexRepresentingOneTransition.costOfBestDeployment +
						vertex.zetaCostOfLeastCostPathForwardFromS;
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				if (vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					priorityQueue.add(vertex.nextVertexRepresentingOneTransition);
					
				} else if (preliminaryDistance < vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromSParent = vertex;

					// rebuild the priority queue
					priorityQueue.remove(vertex.nextVertexRepresentingOneTransition);
					priorityQueue.add(vertex.nextVertexRepresentingOneTransition);
					
					}							
				
			}						

			
			if (vertex.nextVertexRepresentingTwoTransitions != null) {
				
				long preliminaryDistance = vertex.nextVertexRepresentingTwoTransitions.costOfBestDeployment +
						vertex.zetaCostOfLeastCostPathForwardFromS;
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				if (vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					priorityQueue.add(vertex.nextVertexRepresentingTwoTransitions);
					
				} else if (preliminaryDistance < vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromSParent = vertex;

					// rebuild the priority queue
					priorityQueue.remove(vertex.nextVertexRepresentingTwoTransitions);
					priorityQueue.add(vertex.nextVertexRepresentingTwoTransitions);
					
					}							
				
			}						

			/* account for case when harvester stays retracted */
			if (vertex.nextVertexRepresentingStayingRetracted != null) {
				
				long preliminaryDistance = vertex.nextVertexRepresentingStayingRetracted.costOfBestDeployment +
						vertex.zetaCostOfLeastCostPathForwardFromS;
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				if (vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					
					vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					priorityQueue.add(vertex.nextVertexRepresentingTwoTransitions);
					
				} else if (preliminaryDistance < vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
					vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
					vertex.nextVertexRepresentingStayingRetracted.zetaCostOfLeastCostPathForwardFromSParent = vertex;

					// rebuild the priority queue
					priorityQueue.remove(vertex.nextVertexRepresentingStayingRetracted);
					priorityQueue.add(vertex.nextVertexRepresentingStayingRetracted);
					
					}							
				
			}						

			// Get the closest vertex to the source
			vertex = priorityQueue.poll();
			//System.out.println("Dij: " + vertex.costOfBestDeployment);

		}
	    
//		// Check how many nodes are not settled.
//		int nodesNotSettled = 0;
//		for (Vertex v : listRetractedVertex) {
//
//			if (v.bSettled == false) {
//				nodesNotSettled++;
//				}
//			
//		}
//		for (Vertex v : listDeployedVertex) {
//
//			if (v.bSettled == false) {
//				nodesNotSettled++;
//				}
//			
//		}
//		
//		System.out.println("The number of NOT settled nodes are " + nodesNotSettled);
		
		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return;
		
		
	}

	
	// original
	
	
	
	private void calculateShortestPathFromSourceCostDijkstraMonthlyVertex(MonthlyVertex  vertex) {
	    
		
		// Distance from the source to the source is 0
		vertex.zetaCostOfLeastCostPathForwardFromS = 0;
		destinationMonthlyVertex.costOfBestDeployment = 0;

	    // Set<Vertex> settledNodes = new HashSet<Vertex>();
	    // Set<Vertex> unsettledNodes = new HashSet<Vertex>();
	 
	    /* "We add the startNode to the unsettled nodes set." */
	    // unsettledNodes.add(retracted);
	    /* "Choose an evaluation node from the unsettled nodes set, 
	     * the evaluation node should be the one with the lowest distance from the source." */
	    // The lowest distance from node will be the deployed node if it is available
	    
		// Visit
		
		final int PRIORITY_QUEUE_CAPACITY = 7;
		Comparator<MonthlyVertex> queueComparator = new ComparatorMonthlyCostForward();
		PriorityQueue<MonthlyVertex > priorityQueue =
				new PriorityQueue<MonthlyVertex >(PRIORITY_QUEUE_CAPACITY, queueComparator);

		
		while (vertex != null) {

			vertex.bSettled = true; // Vertex is settled; Thus, set weight flag
			System.out.println("The settled cost of vertex " + vertex + " is " +
					vertex.zetaCostOfLeastCostPathForwardFromS);
			if (62367608 == vertex.zetaCostOfLeastCostPathForwardFromS) {
				
				System.out.println("pause");
				
			}
			
			if (vertex.nextVertexRepresentingOneTransition != null) {
														
				long preliminaryDistance = vertex.nextVertexRepresentingOneTransition.costOfBestDeployment +
						vertex.zetaCostOfLeastCostPathForwardFromS;
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				if (vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					priorityQueue.add(vertex.nextVertexRepresentingOneTransition);
					
				} else if (preliminaryDistance < vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
					vertex.nextVertexRepresentingOneTransition.zetaCostOfLeastCostPathForwardFromSParent = vertex;

					// rebuild the priority queue
					priorityQueue.remove(vertex.nextVertexRepresentingOneTransition);
					priorityQueue.add(vertex.nextVertexRepresentingOneTransition);
					
					}							
				
			}						

			
			if (vertex.nextVertexRepresentingTwoTransitions != null) {
				
				long preliminaryDistance = vertex.nextVertexRepresentingTwoTransitions.costOfBestDeployment +
						vertex.zetaCostOfLeastCostPathForwardFromS;
				
				// If candidate for closest vertex has not yet been added to priority queue, add it
				if (vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS == Long.MAX_VALUE) {
					
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;
					
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromSParent = vertex;
					priorityQueue.add(vertex.nextVertexRepresentingTwoTransitions);
					
				} else if (preliminaryDistance < vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS) {
						// update distance
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromS = preliminaryDistance;									
					vertex.nextVertexRepresentingTwoTransitions.zetaCostOfLeastCostPathForwardFromSParent = vertex;

					// rebuild the priority queue
					priorityQueue.remove(vertex.nextVertexRepresentingTwoTransitions);
					priorityQueue.add(vertex.nextVertexRepresentingTwoTransitions);
					
					}							
				
			}						

			// Get the closest vertex to the source
			vertex = priorityQueue.poll();
			//System.out.println("Dij: " + vertex.costOfBestDeployment);

		}
	    
//		// Check how many nodes are not settled.
//		int nodesNotSettled = 0;
//		for (Vertex v : listRetractedVertex) {
//
//			if (v.bSettled == false) {
//				nodesNotSettled++;
//				}
//			
//		}
//		for (Vertex v : listDeployedVertex) {
//
//			if (v.bSettled == false) {
//				nodesNotSettled++;
//				}
//			
//		}
//		
//		System.out.println("The number of NOT settled nodes are " + nodesNotSettled);
		
		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return;
		
		
	}

	
	// original
	private boolean calculateShortestPathFromSourceCostDijkstraMonthlyVertex(Vertex vertex,
			MonthStatistics monthStatistics) {	    
		
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
		
//		while ((vertex != null) && (vertex.localDateTime.getMonth() == 
//				monthStatistics.lastDeployedVertex.localDateTime.getMonth())
//				){
			
		while (true) {
			
			
			if (vertex == null) break;
			
			System.out.println("Month Statistics info: " + monthStatistics.month);
			//TODO: doublecheck
			if (vertex.localDateTime.getMonth() != 
				monthStatistics.lastDeployedVertex.localDateTime.getMonth()) break;
			
			
			vertex.bSettled = true; // Vertex is settled; Thus, set weight flag
			// char dOrR = vertex.bDeployed ? 'D' : 'R';
			// System.out.print("calculateShortestPathFromSourceCostDijkstra Settled:" + dOrR +
			//		vertex.timestep);
					
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
						System.out.println("At priority Queue head:" + ((v1==null) ? "null" : ((v1.bDeployed ? "D" : "R") + 
								v1.timestep)));
						// update the priority queue
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
						System.out.println("At priority Queue head:" + (v1.bDeployed ? "D" : "R") + v1.timestep);
						// rebuild the priority queue?
						priorityQueue.remove(nextVertexCandidateViaKeepingSE);
						priorityQueue.add(nextVertexCandidateViaKeepingSE);
						
					}
			}

			// Get the closest vertex to the source  /* TODO: work out the linking
			Vertex nextVertex = priorityQueue.poll();
			vertex = nextVertex;
			
			if (vertex == null) {
				
				break;
				
			}

		}
	    
//		// Check how many nodes are not settled.
//		int nodesNotSettled = 0;
//		for (Vertex v : listRetractedVertex) {
//			System.out.print("Inspecting R"+ v.timestep);
//			if (v.bSettled == false) {
//				
//				System.out.println(" NOT settled");				
//				nodesNotSettled++;
//				System.exit(0);
//				
//				}
//			
//		}
//		for (Vertex v : listDeployedVertex) {
//			System.out.print("Inspecting D"+ v.timestep);			
//			if (v.bSettled == false) {
//				
//				System.out.println(" NOT settled");				
//				nodesNotSettled++;
//				
//				}
//			
//		}
		
//		System.out.println("1. The number of NOT settled nodes are " + nodesNotSettled);
		
		// Has optimum been found? If weight-limit-violation flag is false
		// for s to t and the Weight of the least-cost path from s-to-t <= W, then
		// optimum has been found.
		// Assuming s is the first retracted node.
		// The destinationVertex is the final node.
		return ((destinationVertex.bZetaWeightLimitViolatedForwardFromS == false)
				&& (destinationVertex.zetaWeightOfLeastCostPathForwardFromS < ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth));
		
			


	}		
	

	
	// start here: recreate source version
	private boolean calculateShortestPathFromDestinationCostDijkstra(Vertex vertex, MonthStatistics monthStatistics) {
	    
		//source.setDistance(0); // Assume that the harvester starts retracted.  This the source is
		//Vertex vertex = listRetractedVertex.get(listRetractedVertex.size()-1);
		

		// Vertex vertex = monthStatistics.lastDeployedVertex;
				
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

		
		while ((vertex != null) &&
				(vertex.localDateTime.getMonth() == 
				monthStatistics.lastDeployedVertex.localDateTime.getMonth())
				){
			
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
						System.out.println("At priority Queue head:" + (v1.bDeployed ? "D" : "R") + v1.timestep);
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

	
	private long g(Vertex vertex, Boolean bPrintTransitions) {

		// source.setDistance(0); // Assume that the harvester starts retracted. This
		// the source is
		//Vertex vertex = listRetractedVertex.get(0);
		
		vertex.zetaCostOfLeastCostPathForwardFromS = 0;
		vertex.zetaWeightOfLeastCostPathBackwardFromT = 0;
		
		long sumWeight = 0;
		long sumCost = 0;

		// The least cost path will be the one that is always deployed when possible
		// Thus, to find the least cost, simply follow the path that is as
		// deployed as possible.
		int monthValueOfFirstVertex = vertex.localDateTime.getMonthValue();		
//		while ((vertex != null) && (monthValueOfFirstVertex == vertex.localDateTime.getMonthValue())) {
		while (true) {
			
			if (vertex == null) break;
			
			if (vertex.timestep == (listDeployedVertex.size()-1)) break;
			
			if (monthValueOfFirstVertex != vertex.localDateTime.getMonthValue()) break;
			
			Vertex previousVertex = null;
			if (bPrintTransitions) {

				System.out.println("OptimumPathCostForward:");
				printTransitions(previousVertex, vertex);

			}
			previousVertex = vertex;
			
			if (vertex.timestep == 18) {
				
				System.out.println("pause");
								
			}
			

			if (!(vertex.bDeployed)) { // vertex is retracted

				if (vertex.changingStateEdge != null) { // get deployed vertex

					// Also, set the distances to the retracted vertex
					// before setting the distances to the deployed vertex
					if (vertex.keepingStateEdge != null) { // retracted
						Vertex retractedVertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);
						retractedVertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight
								+ vertex.keepingStateEdge.visibilityUsedMinutes;

						retractedVertex.zetaCostOfLeastCostPathForwardFromS = sumCost
								+ vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else if (vertex.keepingStateEdge != null) { // get retracted vertex // start here
					
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			} else {

				// vertex is deployed
				if (vertex.keepingStateEdge != null) { // get deployed vertex

					// Also, set the distances to the retracted vertex
					// before setting the distances to the deployed vertex
					if (vertex.changingStateEdge != null) {
						Vertex retractedVertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

						retractedVertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight
								+ vertex.changingStateEdge.visibilityUsedMinutes;

						retractedVertex.zetaCostOfLeastCostPathForwardFromS = sumCost
								+ vertex.changingStateEdge.invertedNetEnergyGainKwh;
					}
					// /\ End of the setting distances to the retracted vertex,
					// which is not on the shortest path from s to t.

					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;
					vertex = listDeployedVertex.get(vertex.keepingStateEdge.timestepOfNextVertex);

				} else // if the deployed vertex is null, then get the retracted vertex

				if (vertex.changingStateEdge != null) { // get retracted vertex

					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;
					vertex = listRetractedVertex.get(vertex.changingStateEdge.timestepOfNextVertex);

				} else {

					vertex = null;

				}

			}

			if (vertex != null) {

				// vertex.omegaWeightOfTheLeastWeightPathForwardFromS = sumWeight;
				// vertex.omegaCostOfTheLeastWeightPathForwardFromS = sumCost;

				vertex.zetaWeightOfLeastCostPathForwardFromS = sumWeight;
				vertex.zetaCostOfLeastCostPathForwardFromS = sumCost;
				
				
				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

			}

		}

		return (sumCost);

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
		int monthValueOfFirstVertex = vertex.localDateTime.getMonthValue();	
		System.out.println("Where is the null: " + vertex.localDateTime.getMonthValue());
		//while ((vertex != null) && (monthValueOfFirstVertex == vertex.localDateTime.getMonthValue())) {
		while (true) {
			
			if (vertex == null) break;
			
			//if (monthValueOfFirstVertex == vertex.localDateTime.getMonthValue()) break;
			
			
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

	
	private long calculateShortestPathFromDestinationCost(Vertex vertex,
			boolean bPrintTransitions) {


		if ((vertex.changingStateParent == null) &&
				(vertex.keepingStateParent == null)) {
			
			System.out.print("Error: Destiantion vertex has been orphaned.");
			System.exit(0);
			
		}
		
		vertex.zetaWeightOfLeastCostPathBackwardFromT = 0;
		vertex.zetaCostOfLeastCostPathBackwardFromT = 0;
		
		long sumWeight = 0;
		long sumCost = 0;

		Vertex previousVertex = null;
		if (bPrintTransitions) {

			System.out.println("OptimumPathCostBackward:");
			printTransitions(previousVertex, vertex);

		}
		previousVertex = vertex;

		
		// The least cost path will be the one that is always deployed when possible
		// Thus, to find the least cost, simply follow the path that is as
		// deployed as possible.
		
		int monthValueOfFirstVertex = vertex.localDateTime.getMonthValue();
		while ((vertex != null) && (monthValueOfFirstVertex == vertex.localDateTime.getMonthValue())) {
			// Take the deployed edge backwards if it exists
			if (!vertex.bDeployed) { // vertex is retracted

				// Also, set the distances to the retracted parent vertex
				// before setting the distances to the deployed parent vertex
				if (vertex.keepingStateParent != null) {

					vertex.keepingStateParent.zetaWeightOfLeastCostPathBackwardFromT = sumWeight
							+ vertex.keepingStateParent.keepingStateEdge.visibilityUsedMinutes;

					vertex.keepingStateParent.zetaCostOfLeastCostPathBackwardFromT = sumCost
							+ vertex.keepingStateParent.keepingStateEdge.invertedNetEnergyGainKwh;
				}
				// /\ End of the setting distances to the retracted vertex,
				// which is not on the shortest path from s to t.

				
				if (vertex.changingStateParent != null) { // add the information of edge to the deployed vertex

					vertex = vertex.changingStateParent;
					sumWeight += vertex.changingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.changingStateEdge.invertedNetEnergyGainKwh;

				} else if (vertex.keepingStateParent != null) { // if deployed vertex is not available, add the info of the edge to the retracted vertex

					vertex = vertex.keepingStateParent;
					sumWeight += vertex.keepingStateEdge.visibilityUsedMinutes;
					sumCost += vertex.keepingStateEdge.invertedNetEnergyGainKwh;

				} else {

					vertex = null;

				}

			} else { // vertex is deployed

				// Also, set the distances to the retracted parent vertex
				// before setting the distances to the deployed parent vertex
				if (vertex.changingStateParent != null) {

					vertex.changingStateParent.zetaWeightOfLeastCostPathBackwardFromT = sumWeight
							+ vertex.changingStateParent.changingStateEdge.visibilityUsedMinutes;

					vertex.changingStateParent.zetaCostOfLeastCostPathBackwardFromT = sumCost
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

				vertex.zetaWeightOfLeastCostPathBackwardFromT = sumWeight;
				vertex.zetaCostOfLeastCostPathBackwardFromT = sumCost;

				if (vertex.bReplenishment) {

					sumWeight = 0;

				}

				if (bPrintTracerCode) {
					// tracer code
					// D or R
					char dOrR = vertex.bDeployed ? 'D' : 'R';
					System.out.println("calculateShortestPathFromDestinationCost" + dOrR + vertex.timestep);
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
		int monthValueOfFirstVertex = vertex.localDateTime.getMonthValue();
		while ((vertex != null) && (monthValueOfFirstVertex == vertex.localDateTime.getMonthValue())) {

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

	public OptimumAlgorithm2StateTransitionsPerMonthMaximum(String localStation, HarvesterModel localHarvester, Workload localWorkload) {

		harvester = localHarvester;
		ws = localWorkload;

		Workload.WindspeedSample sample;
		
		// initialize the set of MonthlyStatistics
		for (int i = 0; i < arrayMonthStatistics.length; i++) {
			
			arrayMonthStatistics[i] = new MonthStatistics();
			
			arrayMonthStatistics[i].month = i;			
			
		}
		// dummy vertices used to build the graph where we find the best
		//  paths through the years of training data
        sourceMonthlyVertex = new MonthlyVertex();	
		destinationMonthlyVertex = new MonthlyVertex();


		
		/* The simulation starts retracted */
		int index_of_first_active_lifting_edge = 0;
		int timestep = 0;
		int prevMonth = -1;
		int arrayMonthStatisticsIndex = -1;
		
		Vertex previousDeployedVertex = null;
		Vertex previousRetractedVertex = null;
		
		while ((sample = ws.getNextWindspeedSampleTraining()) != null) {

			listDeployedVertex.add(new Vertex());
			Vertex deployed = listDeployedVertex.get(timestep);
			deployed.timestep = timestep;
			deployed.bDeployed = true;
			deployed.windspeed = sample.windspeed_knots;

			/* The harvester is already deployed */
			deployed.localDateTime = sample.date;
			int currentMonth = deployed.localDateTime.getMonthValue();
			
			deployed.bReplenishment = (prevMonth != currentMonth); // detect start of new month
			prevMonth = currentMonth;
			
			// If deployed vertex is in a restricted condition, then delete it at all linked
			// edges
			final int START_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR = 22;
			final int END_OF_NIGHTLY_VISIBILITY_BAN_24_HOUR = 7;
 
			/* The harvester is already deployed */
			//deployed.keepingStateEdge.netEnergyGainKwh = (int) harvester.pc
			//		.windPowerKilowattsViaTable(sample.windspeed_knots);
			
			deployed.keepingStateEdge.netEnergyGainKwh =
						(int) harvester.pc.windPowerKilowattsViaTable(sample.windspeed_knots, false);
					
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
			

			/* The harvester is already stowed */
			listRetractedVertex.add(new Vertex());
			Vertex retracted = listRetractedVertex.get(timestep);
			retracted.windspeed = sample.windspeed_knots;
			retracted.timestep = timestep;
			retracted.bReplenishment = deployed.bReplenishment; // deployed and retracted vertices are parallel in time
			if (deployed.bReplenishment) { // a new month is starting

				if (arrayMonthStatisticsIndex >= 0) {
					arrayMonthStatistics[arrayMonthStatisticsIndex].lastDeployedVertex = previousDeployedVertex;
					arrayMonthStatistics[arrayMonthStatisticsIndex].lastRetractedVertex = previousRetractedVertex;
				}

				arrayMonthStatisticsIndex++;
				arrayMonthStatistics[arrayMonthStatisticsIndex].firstRetractedVertex = retracted;
				arrayMonthStatistics[arrayMonthStatisticsIndex].firstDeployedVertex = deployed;
					
			}
			previousDeployedVertex = deployed;
			previousRetractedVertex = retracted;
						
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
		
		// set ending statistic for the final month
		arrayMonthStatistics[arrayMonthStatisticsIndex].lastDeployedVertex = previousDeployedVertex;
		arrayMonthStatistics[arrayMonthStatisticsIndex].lastRetractedVertex = previousRetractedVertex;
		
		// Set the value for the first month because the vertex was not available before the loop started
		arrayMonthStatistics[0].firstRetractedVertex = listRetractedVertex.get(0);


		
		// troubleshooting
//		for (int i=0; i<2; i++) {
//
//			System.out.println("In Constructor: The firstRetractedVertex's timestep is " + 
//					arrayMonthStatistics[i].firstRetractedVertex.timestep);
//
//			
//		}
//		
		
		
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

		// for (int i = 0; i < listDeployedVertex.size(); i++) {

		for (int i = 0; i < 7; i++) {
		
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
	
	
	

	private void buildAcyclicDigraphOfMonthlyStatistics(MonthStatistics[] arrayMonthStatistics) {
		
		// Iterate through all but the last month because
		//  the last month ends at dummy node,
		//  which serves as a destination
		int indexOfNextMonthStatistics = 0;
		sourceMonthlyVertex.nextVertexRepresentingOneTransition
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()];
		
		sourceMonthlyVertex.nextVertexRepresentingTwoTransitions
		= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()];

		int i = 0;
		while (arrayMonthStatistics[i].firstRetractedVertex != null) {


			indexOfNextMonthStatistics++;

			System.out.println("indexOfNextMonthStatistics: " + indexOfNextMonthStatistics);
			
//			System.out.println("i: " + i); 	
//			System.out.println("Is this null: " + arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()]); 				
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingOneTransition
				= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()];
			
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingTwoTransitions
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()];

			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingStayingRetracted
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()];

			
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingOneTransition
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()];
		
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingTwoTransitions
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()];

			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingStayingRetracted
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()];


			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingOneTransition
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()];
		
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingTwoTransitions
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()];

			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingStayingRetracted
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()];

			
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingOneTransition
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()];
		
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingTwoTransitions
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()];

			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingStayingRetracted
			= null;

					
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingOneTransition
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()];
		
			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingTwoTransitions
			= arrayMonthStatistics[indexOfNextMonthStatistics].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()];

			arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingStayingRetracted
			= null;

			
			i++;
		}
		
		i--; // re-do final month in array

	
	/* */
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingOneTransition
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingTwoTransitions
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].nextVertexRepresentingStayingRetracted
	= destinationMonthlyVertex;
	
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingOneTransition
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingTwoTransitions
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].nextVertexRepresentingStayingRetracted
	= destinationMonthlyVertex;
	
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingOneTransition
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingTwoTransitions
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].nextVertexRepresentingStayingRetracted
	= destinationMonthlyVertex;
	
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingOneTransition
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingTwoTransitions
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].nextVertexRepresentingStayingRetracted
	= destinationMonthlyVertex;
	
			
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingOneTransition
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingTwoTransitions
	= destinationMonthlyVertex;
	
	arrayMonthStatistics[i].arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].nextVertexRepresentingStayingRetracted
	= destinationMonthlyVertex;
	
	/* */
	
	
	sourceMonthlyVertex.costOfBestDeployment = 0;
	destinationMonthlyVertex.costOfBestDeployment = 0;	
		
	}
	
	
	public void findOptimumPath() {
		
		for (int i = listDeployedVertex.size()-1;
				i > listDeployedVertex.size()-5;
				i--) {
			
			Vertex v = listDeployedVertex.get(i);
			System.out.println("ABC: The RetractedVertex's timestep is " + 
					v.timestep);

		}
		
		findOptimumPathPreprocess();
		
		// build tree
		buildAcyclicDigraphOfMonthlyStatistics(arrayMonthStatistics);
		
		// troubleshooting.  Print out the best cost for each month
		for (MonthStatistics monthStatistics : arrayMonthStatistics) {
			
			if (monthStatistics.firstRetractedVertex == null) break;
			
			System.out.println("The month statistics for month " + monthStatistics.month + " are ");
			System.out.println(
					"ONE_RISE_ONE_FALL, ONE_RISE, ONE_FALL, BIFURCATED");
			
			for (MonthlyVertex monthlyVertex : monthStatistics.arrayMonthlyVertex) {

				System.out.println(" Case value: " + (monthlyVertex.caseOfVertex) + ":");
				System.out.println("     " + (monthlyVertex.costOfBestDeployment));
				System.out.println("     " + "(monthlyVertex.iBestTimestepToDeploy)=" + monthlyVertex.iBestTimestepToDeploy);
				System.out.println("     " + "(monthlyVertex.iBestTimestepToRetract)=" + monthlyVertex.iBestTimestepToRetract);

				
			}
					
			//System.exit(0);
		}
		
		printGraphInDotLanguage1();
		printMonthlyVertexGraphWrapper(sourceMonthlyVertex);
		

		// Find shortest path to each leaf		
		calculateShortestPathFromSourceCostDijkstraMonthlyVertexWithStayingRetracted(sourceMonthlyVertex);

				
		// Print the path & create appended training file	
		printBestPathMonthlyVertex(true);

		long invertingConstant = (destinationVertex.timestep-1)*maximumNetEnergyGainKwh; 
		long netEnergyGainKwh = invertingConstant -   
				destinationMonthlyVertex.zetaCostOfLeastCostPathForwardFromS;

		System.out.println("The destinationMonthlyVertex.zetaCostOfLeastCostPathForwardFromS is " +
				destinationMonthlyVertex.zetaCostOfLeastCostPathForwardFromS);
		
		System.out.println("The energy harvested is " + netEnergyGainKwh + " kWh.");

		//System.out.println("The energy harvested is " + Long.MAX_VALUE);

		

		// Find the leaf having the shortest path
//		sourceMonthlyVertex.zetaCostOfLeastCostPathForwardFromSParent;
//		
//		System.out.println(x);
//		
//		
//		
//		System.out.println("Completed Preprocessing.");
//		if (!bOptimumFound) {
//			
//			findOptimumPathTreatLabels();
//			System.out.println("Completed Label Treatment.");
//			// Print vertices that last updated the labels
//			printPathCreatedByAlgorithm2();
//			//printGraphInDotLanguage2();
//			
//		}
		
	}
	
	// Pass the destinationMontlyVertex
	private void printBestPathMonthlyVertex(boolean bCreateTrainingFile) {
		
		MonthlyVertex vertex = destinationMonthlyVertex;
		
		while(vertex.zetaCostOfLeastCostPathForwardFromSParent != null) {

				vertex.zetaCostOfLeastCostPathForwardFromSParent.zetaCostOfLeastCostPathForwardFromSChild =
						vertex;			
				vertex = vertex.zetaCostOfLeastCostPathForwardFromSParent;

		}
		
		System.out.println("The path forward is ");
		while(vertex != null) {
			
			if (vertex.caseOfVertex != null) {
			
				switch (vertex.caseOfVertex) 
				{
					
				case ONE_RISE_ONE_FALL:
					
					System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
					System.out.println("Retract " + vertex.iBestTimestepToRetract);
					break;
					
				case ONE_RISE:
					
					System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
					break;
					
					
				case ONE_FALL:
					
					System.out.println("Retract " + vertex.iBestTimestepToRetract);
					break;
	
					
				case BIFURCATED: 
				
					System.out.println("Retract " + vertex.iBestTimestepToRetract);
					System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
					break;
				
	
				case STAY_RETRACTED: 
					
					System.out.println("No Retract ");
					System.out.println("No Deploy ");
					break;
		
					
				}
			}
			
			vertex = vertex.zetaCostOfLeastCostPathForwardFromSChild;
			
		}
		
		// Create training file
		if (bCreateTrainingFile) {
			
			vertex = sourceMonthlyVertex; 
			String desiredState = "r";

			System.out.println("Creating training file.");
			ws.prepareToCopyAndAppend("SLA5", true);
			
			while(vertex != null) {
				
				if (vertex.caseOfVertex != null) {
				
					switch (vertex.caseOfVertex) 
					{
						
					case ONE_RISE_ONE_FALL:
						
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToDeploy);
						System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
						desiredState = "d";
						
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToRetract);
						System.out.println("Retract " + vertex.iBestTimestepToRetract);
						desiredState = "r";
						break;
						
					case ONE_RISE:
						
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToDeploy);
						System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
						desiredState = "d";
						
						break;
						
						
					case ONE_FALL:
						
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToRetract);
						System.out.println("Retract " + vertex.iBestTimestepToRetract);
						desiredState = "r";
						
						break;
		
						
					case BIFURCATED: 
					
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToRetract);
						System.out.println("Retract " + vertex.iBestTimestepToRetract);
						desiredState = "r";
						
						ws.writeTrainingFileUntilTimestep(desiredState, vertex.iBestTimestepToDeploy);
						System.out.println("Deploy " + vertex.iBestTimestepToDeploy);
						desiredState = "d";
						
						break;
					
		
					case STAY_RETRACTED: 
						
						System.out.println("No Retract ");
						System.out.println("No Deploy ");
						break;
			
						
					}
				}
				
				vertex = vertex.zetaCostOfLeastCostPathForwardFromSChild;
				
			}

			// write deployment status until the end of the file and then close file
			ws.writeFinalDeploymentStatusAndCloseAppendedFile(desiredState, true);
			
			
		}
		
	}


	private void findBestContiguousDeploymentWindow_OneFall_Original(
			MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
			
			//MonthlyVertex monthlyVertex = new MonthlyVertex();
		
		    int timestepOfLastVertexOfDeployment = monthStatistics.firstRetractedVertex.timestep + iUsedAllItsAllocatedVisibilityMinutesPerMonth; 
		    Vertex lastVertexOfDeploymentRetracted = listRetractedVertex.get(timestepOfLastVertexOfDeployment);
		    
		    //monthlyVertex.costOfBestDeployment = lastVertexOfDeploymentRetracted.zetaCostOfLeastCostPathForwardFromS;
			monthStatistics.arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].costOfBestDeployment = lastVertexOfDeploymentRetracted.zetaCostOfLeastCostPathForwardFromS;
			monthStatistics.arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].iBestTimestepToRetract = timestepOfLastVertexOfDeployment - ws.iRetractionTimeMinimumMinutes;
			
			return;
		
	}
	
	private void findBestContiguousDeploymentWindow_OneFall(
			MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
			
			long cost = 0;
		    int timestepOfLastVertexOfDeployment = monthStatistics.firstRetractedVertex.timestep + iUsedAllItsAllocatedVisibilityMinutesPerMonth; 
		    
		    Vertex v = listDeployedVertex.get(monthStatistics.firstRetractedVertex.timestep);
			// (deployed section)
			while(v.changingStateEdge.timestepOfNextVertex < timestepOfLastVertexOfDeployment) {
				
				cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
				v = listDeployedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
				
			}
			// fall
			cost += v.changingStateEdge.invertedNetEnergyGainKwh;
			
			// retracted section to end of month
			v = listRetractedVertex.get(v.changingStateEdge.timestepOfNextVertex);
												// +1 to account for edge that points to the first vertex of next month
			while (v.keepingStateEdge.timestepOfNextVertex <= (monthStatistics.lastDeployedVertex.timestep + 1)) {				
				
				cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
				v = listRetractedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
				
			}
				    
		    //monthlyVertex.costOfBestDeployment = lastVertexOfDeploymentRetracted.zetaCostOfLeastCostPathForwardFromS;
			monthStatistics.arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].costOfBestDeployment = cost;
			monthStatistics.arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].iBestTimestepToRetract = timestepOfLastVertexOfDeployment - ws.iRetractionTimeMinimumMinutes;
			
			return;
		
	}



	private void findBestContiguousDeploymentWindow_OneRise(
			MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
		
			//MonthlyVertex monthlyVertex = new MonthlyVertex();
		
			//Simply iterate through the entire month while rising once
		    int timestepOfFirstVertexOfDeployment = (monthStatistics.lastDeployedVertex.timestep - iUsedAllItsAllocatedVisibilityMinutesPerMonth) + 1;
		    
		    //beginning
		    long cost = 0;
		    int timestep = monthStatistics.firstDeployedVertex.timestep;
		    for (; timestep<timestepOfFirstVertexOfDeployment; timestep++) {
		    	
		    	cost += listRetractedVertex.get(timestep).keepingStateEdge.invertedNetEnergyGainKwh;
		    	
		    	
		    }
		    //rise
	    	cost += listRetractedVertex.get(timestep).changingStateEdge.invertedNetEnergyGainKwh;
	    	timestep++;
	    	while (timestep <= (monthStatistics.lastDeployedVertex.timestep)) {
	    						if (listDeployedVertex.get(timestep) == null) {
	    							
	    							System.out.println("Null found at timestep " + timestep);
	    							
	    						}
	    		System.out.println("debug: timestep: " + timestep);				
		    	cost += listDeployedVertex.get(timestep).keepingStateEdge.invertedNetEnergyGainKwh;
		    	timestep++;
	    	}
		    //end	

	    	if (timestepOfFirstVertexOfDeployment == -1) {
		    	
		    	System.out.println("The index is -1.");
		    	System.exit(-1);
		    }
		    
			
		    monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].costOfBestDeployment = cost;
		    monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].iBestTimestepToDeploy = timestepOfFirstVertexOfDeployment;
				
		
			return;
	
	}

	


	
	private void findBestContiguousDeploymentWindow_OneRise_Original(
			MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
		
			//MonthlyVertex monthlyVertex = new MonthlyVertex();
				
		    int timestepOfFirstVertexOfDeployment = (monthStatistics.lastDeployedVertex.timestep - iUsedAllItsAllocatedVisibilityMinutesPerMonth) + 1;
		    
		    if (timestepOfFirstVertexOfDeployment == -1) {
		    	
		    	System.out.println("The index is -1.");
		    	
		    }
		    
		    Vertex firstVertexOfDeploymentRetracted = listRetractedVertex.get(timestepOfFirstVertexOfDeployment);
			
		    monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].costOfBestDeployment = firstVertexOfDeploymentRetracted.zetaCostOfLeastCostPathBackwardFromT;
		    monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].iBestTimestepToDeploy = timestepOfFirstVertexOfDeployment;
				
		    //monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE.ordinal()].costOfBestDeployment = firstVertexOfDeploymentRetracted.zetaCostOfLeastCostPathBackwardFromT;
		
			return;
	
	}
	
	
    private void findBestSeparatedDeploymentWindow_OneFallOneRise(MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
    	
    	//MonthlyVertex monthlyVertex = new MonthlyVertex();

    	// first part of deployment window
    	int timestepOfLastVertexOfFirstDeployment = monthStatistics.firstRetractedVertex.timestep + ws.iRetractionTimeMinimumMinutes;
    	long cost = monthStatistics.firstDeployedVertex.changingStateEdge.invertedNetEnergyGainKwh;
    	Vertex deployedVertexWhereHarvesterBeginsToRetract = monthStatistics.firstDeployedVertex;
    	
    	// retracted portion
    	Vertex v = listRetractedVertex.get(monthStatistics.firstDeployedVertex.changingStateEdge.timestepOfNextVertex);

		int timestepOfFirstVertexOfSecondDeployment = (monthStatistics.lastDeployedVertex.timestep -
				iUsedAllItsAllocatedVisibilityMinutesPerMonth) + 1; // add 1 to account for edge that connects to first vertex of next month 
    	while(v.keepingStateEdge.timestepOfNextVertex <= timestepOfFirstVertexOfSecondDeployment) {
    		
    		cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
    		v = listRetractedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
    		
    	}
    	// second part of deployment window
		cost += v.changingStateEdge.invertedNetEnergyGainKwh;
		Vertex finalRetractedVertexOfRetraction = v;
		
		// 2nd deployed portion
		while(v.keepingStateEdge.timestepOfNextVertex <= monthStatistics.lastDeployedVertex.timestep) {
			
    		cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
    		v = listDeployedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
			
		}
		
		// initialize 
		monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment = cost;	
		monthStatistics.timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise = timestepOfLastVertexOfFirstDeployment;		
		monthStatistics.timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise = finalRetractedVertexOfRetraction.timestep;
    	
		// continue search for best cost
		// remove ends.  Then, add new ends

		while(finalRetractedVertexOfRetraction.changingStateEdge.timestepOfNextVertex <= monthStatistics.lastDeployedVertex.timestep)
		{
			
			
			// Then, adjust total to reflect new deployment
			// subtract the previous ends
			cost -= finalRetractedVertexOfRetraction.changingStateEdge.invertedNetEnergyGainKwh; // subtract cost of deploying during previous 2nd-half deployment;
			cost -= deployedVertexWhereHarvesterBeginsToRetract.changingStateEdge.invertedNetEnergyGainKwh; //subtract cost of retracting during previous 1st-half deployment;
			// subtract cost of being deployed during the first minute of the 2nd deployment
			cost -= (listDeployedVertex.get(finalRetractedVertexOfRetraction.changingStateEdge.timestepOfNextVertex)).keepingStateEdge.invertedNetEnergyGainKwh;
			
			finalRetractedVertexOfRetraction = listRetractedVertex.get(finalRetractedVertexOfRetraction.keepingStateEdge.timestepOfNextVertex);
			
			// add the new ends
			cost += finalRetractedVertexOfRetraction.changingStateEdge.invertedNetEnergyGainKwh;
			cost += deployedVertexWhereHarvesterBeginsToRetract.keepingStateEdge.invertedNetEnergyGainKwh; // add new energy gain before updating vertex
			deployedVertexWhereHarvesterBeginsToRetract =
					listDeployedVertex.get(deployedVertexWhereHarvesterBeginsToRetract.keepingStateEdge.timestepOfNextVertex);
			cost += deployedVertexWhereHarvesterBeginsToRetract.changingStateEdge.invertedNetEnergyGainKwh; //add cost of retracting during 1st-half of deployment;

			
			if (cost < monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment) {
	
	   			monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment =
    					cost;
    			
    			monthStatistics.timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise =
    					timestepOfLastVertexOfFirstDeployment
    					= deployedVertexWhereHarvesterBeginsToRetract.changingStateEdge.timestepOfNextVertex;
    			monthStatistics.timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise =
    					timestepOfFirstVertexOfSecondDeployment = finalRetractedVertexOfRetraction.timestep;

    			
    			
			}
		
		}
    	
		monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].iBestTimestepToDeploy = 
				monthStatistics.timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise;
		monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].iBestTimestepToRetract = 
				monthStatistics.timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise - ws.iRetractionTimeMinimumMinutes;
		
		
    	return;
    	
    }
    
	

	
	// OneFallOneRise	
    private void findBestSeparatedDeploymentWindow_OneFallOneRise_Original(MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
    	
    	//MonthlyVertex monthlyVertex = new MonthlyVertex();

    	    	
    	for (int i = ws.iRetractionTimeMinimumMinutes;
    			 i <=  (iUsedAllItsAllocatedVisibilityMinutesPerMonth - ws.iDeploymentTimeMinimumMinutes);
    			 i++)
    	{

    		int timestepOfLastVertexOfFirstDeployment = monthStatistics.firstRetractedVertex.timestep + i; 
    		Vertex lastVertexOfFirstDeploymentRetracted = listRetractedVertex.get(timestepOfLastVertexOfFirstDeployment);
	    
    		int timestepOfFirstVertexOfSecondDeployment = monthStatistics.lastDeployedVertex.timestep -
    				iUsedAllItsAllocatedVisibilityMinutesPerMonth +  i; 
    		Vertex firstVertexOfDeploymentRetracted = listRetractedVertex.get(timestepOfFirstVertexOfSecondDeployment);
    		
    		long costOfBestDeployment_local =
    				lastVertexOfFirstDeploymentRetracted.zetaCostOfLeastCostPathForwardFromS
    				+
    				firstVertexOfDeploymentRetracted.zetaCostOfLeastCostPathBackwardFromT;
    		
    		if (costOfBestDeployment_local <
    				monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment) {
    
    			if (costOfBestDeployment_local < 0) {
    				
    				System.out.println("Pause");
    				
    			}
    			
    			
    			monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment =
    					costOfBestDeployment_local;
    			//monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment = costOfBestDeployment_local;
    			
    			monthStatistics.timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise =
    					timestepOfLastVertexOfFirstDeployment; // Best is the best first + second deployment
    			monthStatistics.timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise =
    					timestepOfFirstVertexOfSecondDeployment;
    			

    			
    			

//    			System.out.println(i + "monthStatistics.lastDeployedVertex.timestep=" +
//    					monthStatistics.lastDeployedVertex.timestep);
//    			System.out.println("The timestepOfLastVertexOfFirstDeployment is " +
//    					timestepOfLastVertexOfFirstDeployment);
//    			System.out.println("monthStatistics.firstRetractedVertex.timestep: " + monthStatistics.firstRetractedVertex.timestep);
//    			System.out.println("The first timestep of the month is " + 
//    					monthStatistics.firstRetractedVertex.timestep);
//    			System.out.println("The timestepOfFirstVertexOfSecondDeployment is " +
//    					timestepOfFirstVertexOfSecondDeployment + " of " + 
//    					listRetractedVertex.size() + " limit: " +
//    					(iUsedAllItsAllocatedVisibilityMinutesPerMonth - ws.iDeploymentTimeMinimumMinutes));
//    			System.out.println("The timestepOfLastVertexOfFirstDeployment is " +
//    					monthStatistics.lastDeployedVertex.timestep);




    		}
    		
    	}
    					
    	
		monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].iBestTimestepToDeploy = 
				monthStatistics.timestepOfFirstRetractedVertexOfBestSecondDeployment_OneFallOneRise;
		monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].iBestTimestepToRetract = 
				monthStatistics.timestepOfLastRetractedVertexOfBestFirstDeployment_OneFallOneRise - ws.iRetractionTimeMinimumMinutes;
		
		
    	return;
    	
    }
    
	
	// OneRiseOneFall
	private void findBestContiguousDeploymentWindow_OneRiseOneFall(MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
		
		//MonthlyVertex monthlyVertex = new MonthlyVertex();
		int timestepDeploymentStart = monthStatistics.firstRetractedVertex.timestep; // starting RETRACTED vertex of deployment
		int timestepDeploymentEnd = timestepDeploymentStart + iUsedAllItsAllocatedVisibilityMinutesPerMonth;
		
		// start
		long cost = monthStatistics.firstRetractedVertex.changingStateEdge.invertedNetEnergyGainKwh;
		Vertex retractedVertexWhereHarvesterBeginsToDeploy =  monthStatistics.firstRetractedVertex; 
		Vertex v = listDeployedVertex.get(monthStatistics.firstRetractedVertex.changingStateEdge.timestepOfNextVertex);
		
		// middle (deployed section)
		while(v.changingStateEdge.timestepOfNextVertex < timestepDeploymentEnd) {
			
			cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
			v = listDeployedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
			
		}
		
		// end
		/// add cost of retracting
		cost += v.changingStateEdge.invertedNetEnergyGainKwh; //costOfRetracting;
		v = listRetractedVertex.get(v.changingStateEdge.timestepOfNextVertex);
		Vertex finalDeployedVertexOfDeployment = v; 
		
		/// add cost of remaining retracted
		while(v.timestep <= monthStatistics.lastRetractedVertex.timestep) {
			
			cost += v.keepingStateEdge.invertedNetEnergyGainKwh;
			v = listRetractedVertex.get(v.keepingStateEdge.timestepOfNextVertex);
			
		}
		
		// set initial cost, deployments, and retractions
		monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].costOfBestDeployment = cost;
		monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall = timestepDeploymentStart; 				

		
		
		while(finalDeployedVertexOfDeployment.changingStateEdge.timestepOfNextVertex < (monthStatistics.lastDeployedVertex.timestep + 1)) // add one to point to first vertex of next month (which receives the final edge of the current month))
		{
			// Then, adjust total to reflect new deployment
			// subtract the previous ends
			cost -= finalDeployedVertexOfDeployment.changingStateEdge.invertedNetEnergyGainKwh; // subtract cost of retracting during previous deployment;
			cost -= retractedVertexWhereHarvesterBeginsToDeploy.changingStateEdge.invertedNetEnergyGainKwh; //subtract cost of deploying during previous deployment;
			// subtract cost of being deployed during the first minute of the deployment
			cost -= (listDeployedVertex.get(retractedVertexWhereHarvesterBeginsToDeploy.changingStateEdge.timestepOfNextVertex)).keepingStateEdge.invertedNetEnergyGainKwh;
			
			
			finalDeployedVertexOfDeployment = listDeployedVertex.get(finalDeployedVertexOfDeployment.keepingStateEdge.timestepOfNextVertex);
			retractedVertexWhereHarvesterBeginsToDeploy = listRetractedVertex.get(retractedVertexWhereHarvesterBeginsToDeploy.keepingStateEdge.timestepOfNextVertex);
			
			// add the new ends
			cost += finalDeployedVertexOfDeployment.changingStateEdge.invertedNetEnergyGainKwh; 
			cost += retractedVertexWhereHarvesterBeginsToDeploy.changingStateEdge.invertedNetEnergyGainKwh; //subtract cost of deploying during previous deployment;
			// add cost of being deployed during the first minute of the deployment
			cost += (listDeployedVertex.get(retractedVertexWhereHarvesterBeginsToDeploy.changingStateEdge.timestepOfNextVertex)).keepingStateEdge.invertedNetEnergyGainKwh;
			 		
			if (cost < monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].costOfBestDeployment) {
	
				monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].costOfBestDeployment = cost;
				monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall = retractedVertexWhereHarvesterBeginsToDeploy.timestep; 
				
			}
		
		}
			
		monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].iBestTimestepToDeploy = 
					monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall;
		// Upgrade: possibly record last timestep of final vertex in deployment
		monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].iBestTimestepToRetract = 
				monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall +
				ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth -
				ws.iRetractionTimeMinimumMinutes;
		
		
		//System.exit(0);	
			
		return;
		
	}

    
    
    
    // OneRiseOneFall
	private void findBestContiguousDeploymentWindow_OneRiseOneFall_Original(MonthStatistics monthStatistics,
			int iUsedAllItsAllocatedVisibilityMinutesPerMonth) {
		
		//MonthlyVertex monthlyVertex = new MonthlyVertex();
					
		int firstVertexEndingContiguousDeployment = monthStatistics.firstRetractedVertex.timestep +
				iUsedAllItsAllocatedVisibilityMinutesPerMonth;
			// find vertex at the time-limi
			for (int timestepEndingDeploymentRetracted = (monthStatistics.lastDeployedVertex.timestep + 1); // add one to point to first vertex of next month (which receives the final edge of the current month)
					 timestepEndingDeploymentRetracted >= firstVertexEndingContiguousDeployment;
					 timestepEndingDeploymentRetracted--) {
				
				// lastindex.bestcost - firstindexdeployed.bestcost + firstindexdeployed.bestweight
				Vertex vertexEndingDeployment = listRetractedVertex.get(timestepEndingDeploymentRetracted);
				Vertex firstRetractedVertexOfDeployment = 
						listRetractedVertex.get(timestepEndingDeploymentRetracted - iUsedAllItsAllocatedVisibilityMinutesPerMonth);

				Vertex firstDeployedVertexOfDeployment = 
						listDeployedVertex.get(firstRetractedVertexOfDeployment.changingStateEdge.timestepOfNextVertex);

				
				long deploymentCost = vertexEndingDeployment.zetaCostOfLeastCostPathForwardFromS -
						firstDeployedVertexOfDeployment.zetaCostOfLeastCostPathForwardFromS +
						firstDeployedVertexOfDeployment.omegaCostOfTheLeastWeightPathForwardFromS;

				//System.out.println(firstVertexEndingContiguousDeployment + "," + timestepEndingDeploymentRetracted + " deploymentCost test: " + deploymentCost);
				//if (timestepEndingDeploymentRetracted == 21933) {
					
				//	System.out.println("test");
					
				//}
				//System.out.println("The deployment cost is " + deploymentCost);
				
				// DeploymentCost is 0
				if (deploymentCost == 0) {
					
					System.out.println("Exiting here:");
					Integer testInt;
					testInt = null;
					System.out.println(testInt + 1);
					System.exit(3);
								
				}
				
				
				if (deploymentCost < monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].costOfBestDeployment) {

					monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].costOfBestDeployment = deploymentCost;
					monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall = timestepEndingDeploymentRetracted - iUsedAllItsAllocatedVisibilityMinutesPerMonth;
					

					
				}
				
			}
			
		monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].iBestTimestepToDeploy = 
					monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall;
		// Upgrade: possibly record last timestep of final vertex in deployment
		monthStatistics.arrayMonthlyVertex[Cases.ONE_RISE_ONE_FALL.ordinal()].iBestTimestepToRetract = 
				monthStatistics.timestepOfFirstRetractedVertexOfBestDeployment_OneRiseOneFall +
				ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth -
				ws.iRetractionTimeMinimumMinutes;
		
		
		//System.exit(0);	
			
		return;
		
	}

	private boolean calculateShortestPathFromSourceCostDijkstra(Vertex vertex) {
	    
		//source.setDistance(0); // Assume that the harvester starts retracted.  This the source is
		//Vertex vertex = listRetractedVertex.get(0);
		if (vertex == null) System.out.println("The passed vertex is null");
		System.out.println("The passed vertex is " + 
				vertex.timestep);
		
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
		
		// save month of initial vertex
		int monthValueOfInitialVertex = vertex.localDateTime.getMonthValue();
		
		
		while (
				(vertex != null) &&
				(vertex.timestep != destinationVertex.timestep - 1) 
				&& (monthValueOfInitialVertex == vertex.localDateTime.getMonthValue())
				){

			// System.out.println("The destination vertex is " + destinationVertex.timestep);

			//System.out.println("V now is " + vertex.timestep +
			//		" date " + vertex.localDateTime);
			if (
					monthValueOfInitialVertex != vertex.localDateTime.getMonthValue()
					) break;
			
			vertex.bSettled = true; // Vertex is settled; Thus, set weight flag
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
//						if (v1 == null) {
//
//							System.out.println("Peek resulted in null");
//							
//							
//						} else {
//							
//							System.out.println("Test: " + v1.bDeployed + v1.timestep);
//							
//						}
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

	

	private boolean calculateShortestPathFromDestinationCostDijkstra(Vertex vertex) {
	    
		//source.setDistance(0); // Assume that the harvester starts retracted.  This the source is
		//Vertex vertex = listRetractedVertex.get(listRetractedVertex.size()-1);
				
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
	
	
	
	private void printMonthlyVertexGraphWrapper(MonthlyVertex vertex) {
	
		System.out.println("\n digraph G {");
		printMonthlyVertexGraph(vertex);
		System.out.println("\n }");


	}
	
private void printMonthlyVertexGraph(MonthlyVertex vertex) {

		if (vertex.nextVertexRepresentingOneTransition != null) {

			if (!vertex.bEdgeOnePrinted) {
				System.out.print("V" + vertex + " -> ");			
				System.out.println("V" + vertex.nextVertexRepresentingOneTransition
						+ "[ label=\"" + "1Cost: " + vertex.nextVertexRepresentingOneTransition.costOfBestDeployment
						+ "\" ];");
				printMonthlyVertexGraph(vertex.nextVertexRepresentingOneTransition);
				vertex.bEdgeOnePrinted = true;
			}
		}
		if (vertex.nextVertexRepresentingOneTransition != null) {

			if (!vertex.bEdgeTwoPrinted) {			
				System.out.print("V" + vertex + " -> ");			
				System.out.println("V" + vertex.nextVertexRepresentingTwoTransitions
						+ "[ label=\"" + "2Cost: " + vertex.nextVertexRepresentingTwoTransitions.costOfBestDeployment
						+ "\" ];");
				printMonthlyVertexGraph(vertex.nextVertexRepresentingTwoTransitions);
				vertex.bEdgeTwoPrinted = true;
			}
			
		}
	
	}
	
	
	
	
	private void findOptimumPathPreprocess() {

		// check the month statistics
		for (int i=0; i<2; i++) {
			
			System.out.println("For month " + arrayMonthStatistics[i].month + " the first statistiscs are " + 
					arrayMonthStatistics[i].firstRetractedVertex.timestep);
			System.out.println("firstDeployedVertex: " + arrayMonthStatistics[i].firstDeployedVertex.timestep);
			System.out.println("lastDeployedVertex " + arrayMonthStatistics[i].lastDeployedVertex.timestep);
			System.out.println("lastRetractedVertex " + arrayMonthStatistics[i].lastRetractedVertex.timestep);

			
		}
		//System.exit(0);
		//printGraphInDotLanguage1();

			// Since the harvester starts retracted, only two cases apply to the first month.
		    //  Thus, only calculate the two applicable cases.  Set the remaining cases to
			//  the highest possible value.
		    MonthStatistics monthStatistics = arrayMonthStatistics[0];
			calculateShortestPathFromSourceCostDijkstra(monthStatistics.firstRetractedVertex);
			// print the data for the first few vertices of the month
			for (int i=monthStatistics.firstRetractedVertex.timestep; i<monthStatistics.firstRetractedVertex.timestep + 5; i++) {

				Vertex v = listDeployedVertex.get(i);
				System.out.println("d" + v.timestep + " " + v.zetaCostOfLeastCostPathForwardFromS);		

				v = listRetractedVertex.get(i);
				System.out.println("r" + v.timestep + " " + v.zetaCostOfLeastCostPathForwardFromS);		

				
			}
			
			//System.exit(0);			

			
			calculateShortestPathFromSourceWeight(monthStatistics.firstRetractedVertex, false);
			
			// print the data for the first few vertices of the month
			System.out.println("Weights");
			for (int i=monthStatistics.firstRetractedVertex.timestep; i<monthStatistics.firstRetractedVertex.timestep + 5; i++) {

				Vertex v = listDeployedVertex.get(i);
				System.out.println("d" + v.timestep + " " + v.omegaCostOfTheLeastWeightPathForwardFromS);		

				v = listRetractedVertex.get(i);
				System.out.println("r" + v.timestep + " " + v.omegaCostOfTheLeastWeightPathForwardFromS);		

				
			}

			
			
			findBestContiguousDeploymentWindow_OneRiseOneFall(
					monthStatistics, ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);

			//calculateShortestPathFromDestinationCost(monthStatistics.lastDeployedVertex, false);
			calculateShortestPathFromDestinationCostDijkstra(monthStatistics.lastDeployedVertex);
			findBestContiguousDeploymentWindow_OneRise(monthStatistics, ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
			
			monthStatistics.arrayMonthlyVertex[Cases.ONE_FALL.ordinal()].costOfBestDeployment = Long.MAX_VALUE;
			monthStatistics.arrayMonthlyVertex[Cases.BIFURCATED.ordinal()].costOfBestDeployment = Long.MAX_VALUE;
			
			calculateShortestPathFromSourceWeight(monthStatistics.firstRetractedVertex, false);
			// Stay Retracted
			monthStatistics.arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].costOfBestDeployment =					
					listRetractedVertex.get(monthStatistics.lastRetractedVertex.keepingStateEdge.timestepOfNextVertex).							
							omegaCostOfTheLeastWeightPathForwardFromS;
			
		
		    // For each remaining month (i.e., all months not including the first month,
		    //  find the best cost and best path from the month ends
			for (int month = 1; ((month < arrayMonthStatistics.length) &&
					(monthStatistics = arrayMonthStatistics[month]).firstRetractedVertex != null)
					; month++) {
				
				
				System.out.println("The month is " + month);
				System.out.println("The number of month statistics is " + arrayMonthStatistics.length);
			    monthStatistics = arrayMonthStatistics[month];

			    
				// OneRiseOneFall
				calculateShortestPathFromSourceCostDijkstra(monthStatistics.firstRetractedVertex);
				//print out zetaCosts
				// print the data for the first few vertices of the month
	//			for (int i=86399;
	//					 i>(86399 - 20000); i--) {

//				Vertex v;
//				int i=86399;
//				do {
//					v = listDeployedVertex.get(i);
//					System.out.println("d" + v.timestep + " " + v.zetaCostOfLeastCostPathForwardFromS);		
//
//					v = listRetractedVertex.get(i);
//					System.out.println("r " + v.timestep + " " + v.zetaCostOfLeastCostPathForwardFromS);		
//
//					i--;
//					
//				} while (v.zetaCostOfLeastCostPathForwardFromS == 9223372036854775807L);
				
				//System.exit(0);
				
				calculateShortestPathFromSourceWeight(monthStatistics.firstRetractedVertex, false);
				findBestContiguousDeploymentWindow_OneRiseOneFall(
						monthStatistics, ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);

				//System.exit(0);
				
				// OneRise
				//calculateShortestPathFromDestinationCost(monthStatistics.lastDeployedVertex, false);
				calculateShortestPathFromDestinationCostDijkstra(monthStatistics.lastDeployedVertex);
				findBestContiguousDeploymentWindow_OneRise(monthStatistics, ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth); 
				
				//System.exit(2);	
				
				// OneFall
				calculateShortestPathFromSourceCostDijkstra(monthStatistics.firstDeployedVertex);					
				findBestContiguousDeploymentWindow_OneFall(monthStatistics, ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth); 
				
				
				// Separated
				//calculateShortestPathFromDestinationCost(monthStatistics.lastDeployedVertex, false);
				calculateShortestPathFromDestinationCostDijkstra(monthStatistics.lastDeployedVertex);
    			findBestSeparatedDeploymentWindow_OneFallOneRise(monthStatistics,
    							ws.iUsedAllItsAllocatedVisibilityMinutesPerMonth);
    			
    			// Stay Retracted
    			monthStatistics.arrayMonthlyVertex[Cases.STAY_RETRACTED.ordinal()].costOfBestDeployment =					
    					listRetractedVertex.get(monthStatistics.lastRetractedVertex.keepingStateEdge.timestepOfNextVertex).							
    							omegaCostOfTheLeastWeightPathForwardFromS;

    			//populateMonthlyVertexForCaseWhenHarvesterStaysRetracted(monthStatistics);
    			
				
			}
			
			
	}


}


