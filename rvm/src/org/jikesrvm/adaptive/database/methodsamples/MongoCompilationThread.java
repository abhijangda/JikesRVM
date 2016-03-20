package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.runtime.Time;
import org.jikesrvm.scheduler.*;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;

@NonMoving
public class MongoCompilationThread extends SystemThread {	
	public static MongoBlockingQueue queue;
	private boolean firstTimeProcessing;
	
	
	public MongoCompilationThread() {
		super("MongoCompilationThread");
		firstTimeProcessing = false;
	}
	
	@Inline
	public boolean isFirstTimeProcessing ()
	{
		return firstTimeProcessing;
	}
	
	public void run ()
	{
		while (true)
		{
			try {
				if (queue == null)
					continue;
				MongoMethodRecompilationEvent m = ((MongoMethodRecompilationEvent)queue.dequeue());
				m.process();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				VM.sysWriteln ("Errprrrrrrr");
				e.printStackTrace();
			}
		}
	}
	
	public static void enqueueToCompilationThread (NormalMethod m, int cmid, CompiledMethod cm)
	{		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln (Controller.controllerClock + ":" + Time.currentTimeMillis() + ": Enqueue method " + 
					MongoMethodDatabase.getMethodFullDesc(m)+ " to compilation thread");
		if (queue == null)
			return;
		
		MongoMethodRecompilationEvent event = new MongoMethodRecompilationEvent(m, cm);
		queue.enqueue(event);
	}
}