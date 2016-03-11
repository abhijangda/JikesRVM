package org.jikesrvm.adaptive.database.methodsamples;

import static org.jikesrvm.runtime.EntrypointHelper.getField;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.scheduler.Synchronization;

public class MongoBlockingQueue {
	private Object[] array;
	private int start;
	private int end;
	private int capacity;
	public static final RVMField endField =
		      getField(MongoBlockingQueue.class, "end", int.class);
		 
	public MongoBlockingQueue ()
	{
		array = new Object[5000];
		start = 0;
		end = 0;
		this.capacity = 5000;
	}
	
	public MongoBlockingQueue (int capacity)
	{
		this.capacity = capacity;
		array = new Object[capacity];
		start = 0;
		end = 0;
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
	
	public synchronized boolean isEmpty ()
	{
		return start == end;
	}
	
	public Object dequeue ()
	{
		while (isEmpty())
		{
			try {
				//wait ();
				Thread.sleep(1);
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
		
		Object o = array[start];
		start += 1;
		
		return o;
	}
	
	public void enqueue (Object o)
	{
		if (end == this.capacity)
		{
			synchronized(this)
			{
				if (end == this.capacity)
				{
					int newCapacity = this.capacity<<1;
					Object[] newArray = new Object[newCapacity];
					
					for (int i = 0; i < this.capacity; i++)
					{
						newArray[i] = array[i];
					}	

					array = newArray;
					this.capacity = newCapacity;
				}
			}
		}
		
		int p = Synchronization.fetchAndAdd(this, endField.getOffset(), 1);
		array[p] = o;
		
		/*{
			synchronized(this){
				try {
					notifyAll();
				} catch (Exception e) {
					VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
				}
			}
		}*/
	}
}
