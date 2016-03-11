package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.ControllerInputEvent;
import org.jikesrvm.adaptive.controller.HotMethodEvent;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

public class MongoMethodRecompilationEvent implements ControllerInputEvent{

	public final NormalMethod method;
	private CompiledMethod basecompiledMethod;
	
	public MongoMethodRecompilationEvent (NormalMethod meth, CompiledMethod baseCM)
	{
		method = meth;
		basecompiledMethod = baseCM;
	}
	
	@Override
	public void process() {		
		VM.methodDatabase.processMongoCompilationQueue(method, basecompiledMethod.getId(), basecompiledMethod);
	}
}
