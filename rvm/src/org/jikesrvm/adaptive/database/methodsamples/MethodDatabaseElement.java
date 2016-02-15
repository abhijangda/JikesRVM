package org.jikesrvm.adaptive.database.methodsamples;

public class MethodDatabaseElement {
	public final String name;
	public final double count;
	public final int optLevel;
	
	public MethodDatabaseElement(String name, double count, int optLevel) {
		super();
		this.name = name;
		this.count = count;
		this.optLevel = optLevel;
	}
}
