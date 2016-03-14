package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.recompilation.instrumentation.AOSInstrumentationPlan;
import org.jikesrvm.adaptive.util.AOSGenerator;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;

public class MongoControllerPlan {
	public final NormalMethod meth;
	public String methFullDesc;
	public final CompiledMethod prev_cm;
	public final int optLevel;
	public final AOSInstrumentationPlan instrumentationPlan;
	public final CompilationPlan compPlan;
	public final double counts;
	
	public MongoControllerPlan(NormalMethod meth, String methFullDesc,
			CompiledMethod prev_cm, int optLevel, double counts) {
		super();
		this.meth = meth;
		this.methFullDesc = methFullDesc;
		this.prev_cm = prev_cm;
		this.optLevel = optLevel;
		
		instrumentationPlan =
                new AOSInstrumentationPlan(Controller.options, meth);
        compPlan =
                new CompilationPlan(meth,
                                    Controller.recompilationStrategy.getOptPlanForLevel(optLevel),
                                    instrumentationPlan,
                                    Controller.recompilationStrategy.getOptOptionsForLevel(optLevel),
                                    false);
        this.counts = counts;
	}
	
	public void execute ()
	{
		Controller.compilationQueue.mongo_queue_insert(optLevel, counts, this);
	}
	
	public CompiledMethod doRecompile ()
	{
	    if (compPlan.options.PRINT_METHOD) {
	      VM.sysWrite("-oc:O" + compPlan.options.getOptLevel() + " \n");
	    }
	    
	    if (methFullDesc == null)
	    	methFullDesc = MongoMethodDatabase.getMethodFullDesc(meth);
	    
	    if (meth.getCurrentCompiledMethod() instanceof OptCompiledMethod)
	    {
	    	int optLevel = ((OptCompiledMethod)meth.getCurrentCompiledMethod()).getOptLevel();
	    	if (optLevel >= compPlan.options.getOptLevel())
	    	{
	    		if (VM.useAOSDBVerbose)
	    	    	VM.sysWriteln ("Failing AOSDB: Recompiling method " + methFullDesc + " with optLevel " + optLevel + 
	    	    			" as current level is " + optLevel);
	    		
	    		return null;
	    	}
	    }
	    
	    if (VM.useAOSDBVerbose)
	    	VM.sysWriteln ("AOSDB: Recompiling method " + methFullDesc + " with optLevel " + optLevel);
	    // Compile the method.
	    int newCMID = RuntimeCompiler.recompileWithOpt(compPlan);
	    int prevCMID = prev_cm.getId();
	    
	    if (VM.useAOSDBVerbose)
	    	VM.sysWriteln ("AOSDB: Recompiling method " + methFullDesc + " done with newCMID " + newCMID);
	   
	    if (Controller.options.sampling()) {
	      // transfer the samples from the old CMID to the new CMID.
	      // scale the number of samples down by the expected speedup
	      // in the newly compiled method.
	      double expectedSpeedup = 1.0;
	      double oldNumSamples = Controller.methodSamples.getData(prevCMID);
	      double newNumSamples = oldNumSamples / expectedSpeedup;
	      Controller.methodSamples.reset(prevCMID);
	      if (newCMID > -1) {
	        Controller.methodSamples.augmentData(newCMID, newNumSamples);
	      }
	    }
	    	    
	    CompiledMethod cm = newCMID == -1 ? null : CompiledMethods.getCompiledMethod(newCMID);
	    
	    if (Controller.options.ENABLE_ADVICE_GENERATION && (newCMID != -1)) {
	      AOSGenerator.reCompilationWithOpt(compPlan);
	    }
	    
	    return cm;
	}
}
