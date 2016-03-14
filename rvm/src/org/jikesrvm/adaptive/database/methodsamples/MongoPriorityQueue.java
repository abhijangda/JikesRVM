package org.jikesrvm.adaptive.database.methodsamples;

/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

import org.jikesrvm.VM;

/**
 * This class implements a priority queue using the standard
 * (balanced partially-ordered tree, i.e., "heap") algorithm.
 * Smaller priority objects are in the front of the queue.
 */
public class MongoPriorityQueue {

  /**
   * the queue, we use elements 1..queue.length
   */
  private MongoPriorityQueueNode[] queue;

  /**
   * the number of elements actually in the queue
   */
  private int numElements = 0;

  public MongoPriorityQueue() {
    queue = new MongoPriorityQueueNode[1000];

    // We don't use element #0
    for (int i = 1; i < queue.length; i++) {
      queue[i] = new MongoPriorityQueueNode();
    }
  }

  /**
   * Determines number of elements in the queue
   * @return number of elements in the queue
   */
  public final synchronized int numElements() {
    return numElements;
  }

  /**
   * Checks if the queue is empty
   * @return is the queue empty?
   */
  public final synchronized boolean isEmpty() {
    return numElements == 0;
  }

  /**
   * Starting at the position passed, swap with parent until heap condition
   * is satisfied, i.e., bubble up
   * @param startingElement the position to start at
   */
  private void reheapify(int startingElement) {
    int current = startingElement;
    int parent = numElements / 2;
    // keep checking parents that violate the magic condition
    while (parent > 0 && ((queue[parent].optLevel < queue[current].optLevel) || 
    		(queue[parent].optLevel == queue[current].optLevel && queue[parent].counts < queue[current].counts))){
      //        System.out.println("Parent: "+ parent +", Current: "+ current);
      //        System.out.println("Contents before: "+ this);
      // exchange parrent and current values
      MongoPriorityQueueNode tmp = queue[parent];
      queue[parent] = queue[current];
      queue[current] = tmp;

      //        System.out.println("Contents after: "+ this);
      // go up 1 level
      current = parent;
      parent = parent / 2;
    }
  }

  /**
   * Insert the object passed with the priority value passed
   * @param _priority  the priority of the inserted object
   * @param _data the object to insert
   */
  public synchronized void insert(int optLevel, double counts, Object _data) {
    /*if (VM.useAOSDBVerbose)
    {
    	VM.sysWriteln("MongoPriorityQueue.insert() inserting" + optLevel + " " + counts + " " + _data);
    	VM.sysWriteln ("Current Elements: " + toString ());
    }*/
	  
    numElements++;
    if (numElements == queue.length) {
    	MongoPriorityQueueNode[] tmp = new MongoPriorityQueueNode[(int) (queue.length * 1.5)];
      System.arraycopy(queue, 0, tmp, 0, queue.length);
      for (int i = queue.length; i < tmp.length; i++) {
        tmp[i] = new MongoPriorityQueueNode();
      }
      queue = tmp;
    }

    queue[numElements].data = _data;
    queue[numElements].optLevel = optLevel;
    queue[numElements].counts = counts;

    // re-heapify
    reheapify(numElements);
    
    /*if (VM.useAOSDBVerbose)
    {
    	VM.sysWriteln ("After Insertion: " + toString ());
    }*/
  }

  /**
   * Remove and return the front (minimum) object
   * @return the front (minimum) object or null if the queue is empty.
   */
  public synchronized Object deleteMin() {
    if (isEmpty()) return null;
    
    /*if (VM.useAOSDBVerbose)
    {
    	//VM.sysWriteln("MongoPriorityQueue.deleteMin() Deleting" + queue[1].toString());
    	//VM.sysWriteln ("Current Elements: " + toString ());
    }*/
    
    Object returnValue = queue[1].data;
    // move the "last" element to the root and reheapify by pushing it down
    queue[1].optLevel = queue[numElements].optLevel;
    queue[1].counts = queue[numElements].counts;
    queue[1].data = queue[numElements].data;
    numElements--;
    
    // reheapify!!!
    int current = 1;

    // The children live at 2*current and  2*current+1
    int child1 = 2 * current;
    while (child1 <= numElements) {
      int child2 = 2 * current + 1;

      // find the smaller of the two children
      int smaller;
      if (child2 <= numElements && ((queue[child2].optLevel > queue[child1].optLevel)  ||
    		  (queue[child2].optLevel == queue[child1].optLevel && queue[child2].counts > queue[child1].counts))){
        smaller = child2;
      } else {
        smaller = child1;
      }

      if (!((queue[smaller].optLevel > queue[current].optLevel)  ||
    		  (queue[smaller].optLevel == queue[current].optLevel && queue[smaller].counts > queue[current].counts))) {
        break;
      } else {
        // exchange parrent and current values
        MongoPriorityQueueNode tmp = queue[smaller];
        queue[smaller] = queue[current];
        queue[current] = tmp;

        // go down 1 level
        current = smaller;
        child1 = 2 * current;
      }
    }
    
    /*if (VM.useAOSDBVerbose)
    {
    	//VM.sysWriteln ("After deletion current elements: " + toString ());
    }*/
    
    return returnValue;
  }

  /**
   *  Prints the contents of the queue
   *  @return the queue contents
   */
  @Override
  public synchronized String toString() {
    final StringBuilder sb = new StringBuilder(" --> ");
    sb.append("Dumping Queue with ");
    sb.append(numElements);
    sb.append(" elements:\n");
    if (numElements >= 1) sb.append("\t");

    for (int i = 1; i <= numElements; i++) {
      sb.append(queue[i].toString());
      if (i < numElements) sb.append("\n\t");
    }
    return sb.toString();
  }

  /**
   * A local class that holds the nodes of the priority tree
   */
  private static class MongoPriorityQueueNode {

    /**
     * the value to compare on, larger is better
     */
    public int optLevel;
    public double counts;
    /**
     * the associated data
     */
    public Object data;

    @Override
    public String toString() {
      return data + " ... [" + optLevel + ", " + counts + "]";
    }
  }
}

