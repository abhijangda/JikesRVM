package org.jikesrvm.adaptive.database.methodsamples;

public abstract class MethodDatabase {
	public abstract void initialize ();
	public abstract void incrementCallCount (int cmid);
	public abstract double getCallCount (int cmid);
	public abstract void putCallCount(int cmid, double counts);
}
