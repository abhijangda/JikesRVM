package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoOptCompile;

public abstract class MethodDatabase {
	protected int bulkUpdateCount;
	public abstract void initialize ();
	
	public abstract void incrementCallCount (int cmid);
	public abstract double getCallCount (int cmid);
	
	public abstract void putCallCount(int cmid, double counts);
	public abstract void putMethodOptLevel (CompilationPlan cp);
	public abstract int getOptLevelForMethod (RVMMethod m);
	
	protected MethodDatabase (int bulkUpdateCount)
	{
		this.bulkUpdateCount = bulkUpdateCount;
	}
}
