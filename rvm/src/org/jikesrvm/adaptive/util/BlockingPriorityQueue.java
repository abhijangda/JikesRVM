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
package org.jikesrvm.adaptive.util;

import org.jikesrvm.VM;
import org.jikesrvm.util.PriorityQueueRVM;
import org.vmmagic.pragma.Inline;

/**
 * This class extends PriorityQueueRVM to safely
 * support multiple producers/consumers where
 * the consumers are blocked if no objects are available
 * to consume.
 */
public class BlockingPriorityQueue extends PriorityQueueRVM {
	private Object[] array;
	private int start;
	private int end;
	private int capacity;
	@Inline
	public void init_seq_queue ()
	{
		array = new Object[1000];
		start = 0;
		end = 0;
		this.capacity = 1000;
	}
	
	@Inline
	public synchronized boolean seq_queue_isEmpty ()
	{
		return start == end;
	}
	@Inline
	public synchronized Object seq_queue_dequeue ()
	{		
		Object o = array[start];
		start += 1;
		return o;
	}
	@Inline
	public synchronized void seq_queue_enqueue (Object o)
	{
		if (end == this.capacity)
		{
			int newCapacity = 2*this.capacity;
			Object[] newArray = new Object[newCapacity];
			
			for (int i = 0; i < this.capacity; i++)
			{
				newArray[i] = array[i];
			}
			
			array = newArray;
			this.capacity = newCapacity;
		}
		
		array[end] = o;
		end += 1;
		
		try {
		      notifyAll();
		    } catch (Exception e) {
		      // TODO: should we exit or something more dramatic?
		      VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
		    }
	}
	
	/*public synchronized boolean isStackEmpty ()
	{
		return end == 0;
	}
	
	public void push (Object o)
	{
		synchronized(this){
		if (end == this.capacity)
		{
			{
				int newCapacity = 2*this.capacity;
				Object[] newArray = new Object[newCapacity];

				for (int i = 0; i < this.capacity; i++)
				{
					newArray[i] = array[i];
				}

				array = newArray;
				this.capacity = newCapacity;
			}
		}
		
		//int p = Synchronization.fetchAndAdd(this, endField.getOffset(), 1);
		array[end] = o;
		end = end + 1;
		{
			{
				try {
					notifyAll();
				} catch (Exception e) {
					VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
				}
			}
		}
		}
	}
	
	public synchronized Object pop ()
	{	synchronized(this){
		while (isStackEmpty())
		{
			try {
				wait ();
			} catch (IllegalMonitorStateException e) {
				// TODO Auto-generated catch block
				VM.sysWrite("Exception occurred while waiting for element to be inserted!\n");
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				VM.sysWrite("Exception occurred while waiting for element to be inserted!\n");
				e.printStackTrace();
			}
		}
		
		//int p = Synchronization.fetchAndDecrement(this, endField.getOffset(), 1);
		end = end - 1;
		return array[end];
	}
	}*/
  /**
   * Used to notify consumers when about to wait and when notified
   * Default implementation does nothing, but can be overriden as needed by client.
   */
  public static class CallBack {
    public void aboutToWait() {}

    public void doneWaiting() {}
  }

  CallBack callback;
  
  /**
   * @param _cb the callback object
   */
  public BlockingPriorityQueue(CallBack _cb) {
    super();
    callback = _cb;
    init_seq_queue ();
  }

  public BlockingPriorityQueue() {
    this(new CallBack());
    init_seq_queue ();
  }

  /**
   * Insert the object passed with the priority value passed.<p>
   *
   * Notify any sleeping consumer threads that an object
   * is available for consumption.
   *
   * @param _priority  the priority to
   * @param _data the object to insert
   */
  @Override
  public final synchronized void insert(double _priority, Object _data) {
    super.insert(_priority, _data);
    try {
      notifyAll();
    } catch (Exception e) {
      // TODO: should we exit or something more dramatic?
      VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
    }
  }

  /**
   * Remove and return the front (minimum) object.  If the queue is currently
   * empty, then block until an object is available to be dequeued.
   *
   * @return the front (minimum) object.
   */
  @Override
  public final synchronized Object deleteMin() {
    // While the queue is empty, sleep until notified that an object has been enqueued.
    while (seq_queue_isEmpty () && isEmpty()) {
      try {
        callback.aboutToWait();
        wait();
        callback.doneWaiting();
      } catch (InterruptedException e) {
        // TODO: should we exit or something more dramatic?
        VM.sysWrite("Interrupted Exception occurred!\n");
      }
    }
    
    if (!seq_queue_isEmpty ())
    	// 	When we get to here, we know the queue is non-empty, so dequeue an object and return it.
    	return seq_queue_dequeue();
    else
    	return super.deleteMin();
  }
}
