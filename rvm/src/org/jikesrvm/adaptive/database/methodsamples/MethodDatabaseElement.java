package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.classloader.NormalMethod;

public class MethodDatabaseElement {
	public final String name;
	public final double count;
	public final int optLevel;
	public int baseCMID;
	public NormalMethod m;
	
	public MethodDatabaseElement(String name, double count, int optLevel) {
		super();
		this.name = name;
		this.count = count;
		this.optLevel = optLevel;
	}
}
