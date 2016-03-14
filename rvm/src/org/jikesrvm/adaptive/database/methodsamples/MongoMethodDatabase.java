package org.jikesrvm.adaptive.database.methodsamples;

import java.lang.instrument.Instrumentation;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.Semaphore;
//Run with AOS:log
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.database.callgraph.PartialCallGraph;
import org.jikesrvm.adaptive.database.methodsamples.MongoMethodDatabase.WriteRequest.WriteRequestType;
import org.jikesrvm.adaptive.recompilation.InvocationCounts;
import org.jikesrvm.adaptive.recompilation.instrumentation.AOSInstrumentationPlan;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.adaptive.util.CompilerAdvice;
import org.jikesrvm.adaptive.util.CompilerAdviceAttribute;
import org.jikesrvm.adaptive.util.DynamicCallFileInfoReader;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoOptCompile;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

public class MongoMethodDatabase {
	private Mongo mongo;
	private DB aosDatabase;
	private DBCollection aosCollection;
	private DBCollection dcgCollection;
	private BulkWriteThread bulkWriteThread;
	private Semaphore writeRequestsLock;
	private HashMap<String, MethodDatabaseElement> internalDB;
	private int bulkUpdateCount;
	private Vector<MethodToCompileAsync> methodsToCompileAsync;
	private List<WriteRequest> writeRequests;
	private boolean readingCompleted;
	public final MongoCompilationThread compThread;
	private boolean initialized;
	
	static class MethodToCompileAsync
	{
		public final String desc;
		public Integer cmid;
		public final NormalMethod normalMethod;
		public final MethodDatabaseElement elem;
		public MethodToCompileAsync(String desc, Integer cmid,
				NormalMethod normalMethod) {
			super();
			this.desc = desc;
			this.cmid = cmid;
			this.normalMethod = normalMethod;
			this.elem = null;
		}
		
		public MethodToCompileAsync(MethodDatabaseElement elem,
				NormalMethod normalMethod) {
			super();
			this.desc = elem.name;
			this.normalMethod = normalMethod;
			this.elem = elem;
		}
	}
	static class WriteRequest
	{
		public final int cmid;
		public final double count;
		public final int optLevel;
		
		enum WriteRequestType
		{
			IncrementRequest,
			PutRequest,
			UpdateOptLevelRequest,
		}
		
		public final WriteRequestType type;
		
		public WriteRequest(int cmid, double count, int optLevel, WriteRequestType type) {
			super();
			this.cmid = cmid;
			this.count = count;
			this.optLevel = optLevel;
			this.type = type;
		}
		
		public int getCmid() {
			return cmid;
		}
		public double getCount() {
			return count;
		}
		public int getOptLevel() {
			return optLevel;
		}
		
	}
	
	class BulkWriteThread extends Thread
	{
		private DBCollection aosCollection;
		private List<WriteRequest> writeRequests;
		private Semaphore writeRequestsLock;
		
		public BulkWriteThread (DBCollection aosCollection,
				List<WriteRequest> writeRequests, Semaphore writeRequestsLock)
		{
			this.writeRequestsLock = writeRequestsLock;
			this.aosCollection = aosCollection;
			this.writeRequests = writeRequests;
			//this.compPlanLock = compPlanLock;
			//this.compilationPlans = compilationPlans;
		}
		
		public void run ()
		{			
			try {
				if (writeRequests == null)
					return;
			
				writeRequestsLock.acquire();
				WriteRequest[] _writeRequests = new WriteRequest[writeRequests.size()];
				int i = 0;
				for (WriteRequest req : writeRequests)
				{
					_writeRequests[i] = req;
					i++;
				}
				writeRequests.clear();
				writeRequestsLock.release();
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Writing requests");
				
				for (WriteRequest req : _writeRequests)
				{
					if (req.type == WriteRequestType.PutRequest)
					{
						putCallCount(req);
					}
					else if (req.type == WriteRequestType.IncrementRequest)
					{
						incrementCallCount (req);
					}
					else if (req.type == WriteRequestType.UpdateOptLevelRequest)
					{
						updateOptLevel (req);
					}
				}
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Wrote requests");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		public void incrementCallCount (WriteRequest writeRequest)
		{
			double count = 0;
			int cmid = writeRequest.cmid;
			int optLevel = writeRequest.optLevel;
			String methodFullDesc = getMethodFullDesc(cmid);
			if (methodFullDesc == "")
				return;
			
			DBObject query = new BasicDBObject ("methodFullDesc", methodFullDesc);
			DBCursor cursor = aosCollection.find (query);

			DBObject meth = new BasicDBObject();
			meth.put("methodFullDesc", methodFullDesc);
			
			int found = cursor.count();
			if (found > 0)
				count = ((Double)cursor.next().get("count")).doubleValue();
			if (found > 0)
				meth.put("count", count + 1);
			else
				meth.put("count", 1.0);
			meth.put("optLevel", optLevel);
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Method " + methodFullDesc + " with call counts " + count + " will be incremented");
			
			if (found > 0)
			{				
				DBObject updateObj = new BasicDBObject ();
				updateObj.put ("$set", meth);
				
				aosCollection.update(query, updateObj);
			}
			else
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + methodFullDesc + " added to MongoDB");
				
				aosCollection.insert(meth);
			}
		}
		
		public void updateOptLevel (WriteRequest writeRequest)
		{
			int cmid = writeRequest.cmid;
			int optLevel = writeRequest.optLevel;
			double counts;
			String methodFullDesc = getMethodFullDesc (cmid);
			
			if (methodFullDesc == "")
				return;
			
			DBObject query = new BasicDBObject ("methodFullDesc", methodFullDesc);
			DBCursor cursor = aosCollection.find (query);

			DBObject meth = new BasicDBObject();
			meth.put("methodFullDesc", methodFullDesc);
			if (cursor.count() > 0)
				meth.put("count", ((Double)cursor.next().get("count")).doubleValue());
			else
				meth.put("count", 0.0);
			meth.put("optLevel", optLevel);
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Update Method " + methodFullDesc + " with opt level " + optLevel);
			
			if (cursor.count() == 0)
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + methodFullDesc + " added to MongoDB");
				
				aosCollection.insert(meth);
			}
			else
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + methodFullDesc + " updated to MongoDB");
								
				DBObject updateObj = new BasicDBObject ();
				updateObj.put ("$set", meth);
				
				aosCollection.update(query, updateObj);
			}
		}
		
		public void putCallCount (WriteRequest writeRequest)
		{
			int cmid = writeRequest.cmid;
			int optLevel = writeRequest.optLevel;
			double counts = writeRequest.count;
			String methodFullDesc = getMethodFullDesc (cmid);
			
			if (methodFullDesc == "")
				return;
			
			DBObject meth = new BasicDBObject();
			meth.put("methodFullDesc", methodFullDesc);
			meth.put("count", counts);
			meth.put("optLevel", optLevel);
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Put Method " + methodFullDesc + " with call counts " + counts);
			
			if (getCallCount (methodFullDesc) == 0.0)
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + methodFullDesc + " added to MongoDB");
				
				aosCollection.insert(meth);
			}
			else
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + methodFullDesc + " updated to MongoDB");
				
				DBObject query = new BasicDBObject ();
				query.put("methodFullDesc", methodFullDesc);
				
				DBObject updateObj = new BasicDBObject ();
				updateObj.put ("$set", meth);
				
				aosCollection.update(query, updateObj);
			}
		}
	}
	
	public MongoMethodDatabase (int bulkMethodCount)
	{
		this.bulkUpdateCount = bulkMethodCount;
		writeRequests = new LinkedList<WriteRequest> ();
		writeRequestsLock = new Semaphore (1);
		VM.sysWriteln ("RequestsLock created");
		internalDB = new HashMap<String, MethodDatabaseElement> ();
		methodsToCompileAsync = new Vector<MethodToCompileAsync> ();
		compThread = new MongoCompilationThread();
		initialized = false;
	}
	
	public void initializeMongoDB ()
	{		
		try {
			mongo = new Mongo("localhost", 27017);
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("MongoClient Initialized");

		aosDatabase = mongo.getDB("AOSDatabase");
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Mongo AOS Database Initialized");

		aosCollection = aosDatabase.getCollection("AOSCollection");
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Mongo AOS Collection Initialized");
		
		dcgCollection = aosDatabase.getCollection("DCGCollection");
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Mongo AOS Collection Initialized");
		
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			VM.sysWriteln ("EROOORRRRRRRRRRRRRRRR");
			e.printStackTrace();
		}
		
		compThread.start();
		initialized = true;
	}
	
	@Inline
	public boolean isInitialized ()
	{
		return initialized;
	}
	
	public static String getMethodFullDesc (RVMMethod nm)
	{
		RVMClass declaringClass = nm.getDeclaringClass();
		String desc = "";
		if (declaringClass.getDescriptor() == null)
			VM.sysWriteln ("class desc null");
		else
			desc = declaringClass.getDescriptor().toString();
		String methodDesc = "";
		if (nm.getDescriptor() != null)
			methodDesc = nm.getDescriptor().toString();
		
		String name = "";
		if (nm.getName() == null)
			VM.sysWriteln ("name null ");
		else
			name = nm.getName().toString();
		
		return desc + name + methodDesc;
	}
	
	private int getOptLevel (int cmid)
	{
		CompiledMethod cm = CompiledMethods.getCompiledMethodUnchecked(cmid);

		if (cm instanceof OptCompiledMethod)
		{
			return ((OptCompiledMethod)cm).getOptLevel();
		}
		
		return -1;
	}
	
	public MethodDatabaseElement getMethodOptLevel (NormalMethod m)
	{
        String methodFullDesc = getMethodFullDesc (m);
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("getMethodOptLevel: Getting opt level for Method "+ methodFullDesc);
		
		MethodDatabaseElement elem = internalDB.get(methodFullDesc);
		
		if (elem == null)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("getMethodOptLevel: Error:  Cannot find opt level for Method " + methodFullDesc);
			
			if (!readingCompleted)
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln("getMethodOptLevel: Reading not completed will see it later");
			
				//methodsToCompileAsync.add(new MethodToCompileAsync(elem, m));
			}
			
			return null;
		}
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("getMethodOptLevel: Success: Opt Level for Method " + methodFullDesc + " is " + elem.optLevel);
		
		if (elem.optLevel == -1)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("getMethodOptLevel: Optlevel is -1");
			
			return null;
		}
		
		if (!readingCompleted)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("getMethodOptLevel: Reading not completed " + methodFullDesc + " will see it later");
			
			//methodsToCompileAsync.add(new MethodToCompileAsync(elem, m));
			return null;
		}
		
		elem.m = m;
		
		return elem;
	}
	
	@Inline
	public void methodOptCompile (NormalMethod m, int cmid)
	{		
		String methodFullDesc = getMethodFullDesc (m);
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Getting opt level for Method "+ methodFullDesc);
		
		MethodDatabaseElement elem = internalDB.get(methodFullDesc);
		
		if (elem == null)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Error: Cannot find opt level for Method " + methodFullDesc);
			
			if (!readingCompleted)
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln("Reading not completed will see it later");
			
				methodsToCompileAsync.add(new MethodToCompileAsync(methodFullDesc, cmid, m));
			}
			
			return;
		}
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Success: Opt Level for Method " + methodFullDesc + " is " + elem.optLevel);
		
		if (elem.optLevel == -1)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Basecompiling as optlevel is -1");
			
			return;
		}
		
		if (!readingCompleted)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Reading not completed " + methodFullDesc + " will see it later");
			
			methodsToCompileAsync.add(new MethodToCompileAsync(methodFullDesc, cmid, m));
			return;
		}
		
		elem.m = m;
		elem.baseCMID = cmid;
		
		putOptCompilationOnQueue (elem);
	}
	
	public void processMongoCompilationQueue (NormalMethod m, int cmid, CompiledMethod cm)
	{
		String methodFullDesc = getMethodFullDesc (m);
		
		if (methodFullDesc.contains("org/jikesrvm"))
			return;
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Processing Mongo Compilation Queue Entry " + methodFullDesc);
		
		DBObject query = new BasicDBObject ("methodFullDesc", methodFullDesc);
		DBCursor cursor = aosCollection.find (query);
		
		if (cursor.count () == 0)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Error: Cannot find " + methodFullDesc + " on Mongo Method Database");
			
			return;
		}
		
		DBObject doc = cursor.next();
		int optLevel = ((Integer)doc.get("optLevel")).intValue();
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Success: Found " + methodFullDesc + " optLevel as " + optLevel);
		
		if (optLevel == -1)
			return;
		
		double counts = ((Double)doc.get("count")).doubleValue();
		
		/*
		 * query = new BasicDBObject ("callerRef", m.getMemberRef().toString());
		cursor = dcgCollection.find (query);
		
		if (cursor.count () == 0)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Error: Cannot find DCG Entry for " + m.getMemberRef().toString() + " on Mongo Method Database");
		}
		else
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Success: Found " +cursor.count () + " DCG Entries for " + m.getMemberRef().toString() + " optLevel as " + optLevel);
			
			while (cursor.hasNext())
			{
				DBObject d = cursor.next();
				String s = (String)d.get("callerRef");
				s = s.replaceAll("\\{urls[^\\}]*\\}", ""); // strip classloader cruft we can't parse
		        StringTokenizer parser = new StringTokenizer(s, " \n,");
		        
				if (VM.useAOSDBVerbose)
				{
					VM.sysWriteln ("Read DCG Entry: callerRef: " + (String)d.get("callerRef") +  
							" callerLength: " + (Integer)d.get("callerLength") +
							" callerIndex: " + (Integer)d.get("callerIndex") +
							" calleeRef: " + (String)d.get("calleeRef") +
							" calleeLength: " + (Integer)d.get("calleeLength") +
							" weight: " + (Double)d.get("weight"));
					VM.sysWriteln ("Creating DCG Entry");
				}
				
				MemberReference callerKey = MemberReference.parse(parser, false);
				if (callerKey == null) return;
				MethodReference callerRef = callerKey.asMethodReference();
				RVMMethod caller, callee;
				caller = DynamicCallFileInfoReader.getMethod(callerRef);

				int callerSize = (Integer)d.get("callerLength");
				int bci = (Integer)d.get("callerIndex");
				
				s = (String)d.get("calleeRef");
				s = s.replaceAll("\\{urls[^\\}]*\\}", ""); // strip classloader cruft we can't parse
		        parser = new StringTokenizer(s, " \n,");
				
				MemberReference calleeKey = MemberReference.parse(parser, false);
				if (calleeKey == null) return;
				MethodReference calleeRef = calleeKey.asMethodReference();
				callee = DynamicCallFileInfoReader.getMethod(calleeRef);

				int calleeSize = (Integer)d.get("calleeLength");

				float weight = (float)((Double)(d.get("weight"))).doubleValue();
				if ((caller == null) || (callee == null)) {
					Controller.dcg.incrementUnResolvedEdge(callerRef, bci, calleeRef, weight);
				} else {
					Controller.dcg.incrementEdge(caller, bci, callee, weight);
				}
				
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("DCG Entry Created");
			}
		}
		*/
		
		if (VM.useAOSDBVerbose)
				VM.sysWriteln("Putting ControllerPlan on Compilation Queue for method: " + methodFullDesc);
			
		MongoControllerPlan plan = new MongoControllerPlan(m, methodFullDesc, cm, optLevel, counts);
		plan.execute();
	}
	
	@Inline
	public void putOptCompilationOnQueue (MethodDatabaseElement elem)
	{
		if (VM.useAOSDBVerbose)
			VM.sysWriteln("Putting ControllerPlan on Compilation Queue for method: " + elem.name);
		
		AOSInstrumentationPlan instrumentationPlan =
                new AOSInstrumentationPlan(Controller.options, elem.m);
            CompilationPlan compPlan =
                new CompilationPlan(elem.m,
                                    Controller.recompilationStrategy.getOptPlanForLevel(elem.optLevel),
                                    instrumentationPlan,
                                    Controller.recompilationStrategy.getOptOptionsForLevel(elem.optLevel),
                                    false);
            
        ControllerPlan cp = new ControllerPlan (compPlan, 1000, elem.baseCMID, 10000, 10, elem.count);
    	cp.execute();
	}
	
	public static String getMethodFullDesc (int cmid)
	{
		CompiledMethod cm = CompiledMethods.getCompiledMethodUnchecked(cmid);
		if (cm == null)
			return "";
		
		return getMethodFullDesc (cm.method);
	}
	
	@Inline
	public void incrementCallCount(int cmid) {
		// TODO Auto-generated method stub
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Increment Call Count");

		if (writeRequestsLock == null)
		{
			return;
		}
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Initializing: Method " + cmid + " to increment in MongoDB");
		
		int optLevel = getOptLevel (cmid);

		try {
			writeRequestsLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		writeRequests.add(new WriteRequest (cmid, -1, optLevel, 
				WriteRequest.WriteRequestType.IncrementRequest));
		
		writeRequestsLock.release();
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Method " + cmid + " will be incremented to MongoDB");
		
		if (writeRequests.size() >= bulkUpdateCount  && aosCollection != null)
			bulkWrite ();
	}

	public void updateMethodOptLevel (NormalMethod m, CompiledMethod cm)
	{
		if (writeRequestsLock == null)
		{
			return;
		}
		
		int cmid = cm.getId();
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Initializing: Method " + cmid + " to put in MongoDB");
		
		int optLevel = getOptLevel (cmid);
		
		try {
			writeRequestsLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		writeRequests.add(new WriteRequest (cmid, -1, optLevel, 
				WriteRequest.WriteRequestType.UpdateOptLevelRequest));
		
		writeRequestsLock.release();
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Method " + cmid + " will be added to MongoDB");
		
		if (writeRequests.size() >= bulkUpdateCount && aosCollection != null)
			bulkWrite ();
	}
	
	@Inline
	public void putCallCount(int cmid, double counts) {
		// TODO Auto-generated method stub
		//MongoDB implementation
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Put Call Count");
		
		if (writeRequestsLock == null)
		{
			return;
		}
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Initializing: Method " + cmid + " to put in MongoDB");
		
		int optLevel = getOptLevel (cmid);
		
		try {
			writeRequestsLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		writeRequests.add(new WriteRequest (cmid, -1, optLevel, 
				WriteRequest.WriteRequestType.PutRequest));
		
		writeRequestsLock.release();
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Method " + cmid + " will be added to MongoDB");
		
		if (writeRequests.size() >= bulkUpdateCount && aosCollection != null)
			bulkWrite ();
	}
	
	public int getOptLevel (NormalMethod meth)
	{
		// TODO Auto-generated method stub
		if (aosCollection == null)
		{
			return -1;
		}

		String methodFullDesc = getMethodFullDesc (meth);

		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Getting optlevel for Method "+ methodFullDesc);

		DBObject query = new BasicDBObject ("methodFullDesc", methodFullDesc);
		DBCursor cursor = aosCollection.find (query);

		if (cursor.count () == 0)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Cannot find opt level for Method " + methodFullDesc);

			return -1;
		}

		int optLevel = ((Integer)cursor.next().get("optLevel")).intValue();

		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Call count for Method " + methodFullDesc + " is " + optLevel);
		return optLevel;
	}
	
	private double getCallCount (String methodFullDesc)
	{
		//Document meth = aosCollection.find(eq("methodFullDesc", methodFullDesc)).first();
		
		DBObject query = new BasicDBObject ("methodFullDesc", methodFullDesc);
		DBCursor cursor = aosCollection.find (query);
		
		if (cursor.count () == 0)
		{
			return 0.0;
		}
		
		return ((Double)cursor.next().get("count")).doubleValue();
	}
	
	public double getCallCount(int cmid) {
		// TODO Auto-generated method stub
		if (aosCollection == null)
		{
			return 0.0;
		}
		
		String methodFullDesc = getMethodFullDesc (cmid);

		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Getting call count for Method "+ cmid);
		
		double counts = getCallCount (methodFullDesc);
		
		if (counts == 0.0 && VM.useAOSDBVerbose)
			VM.sysWriteln ("Cannot find call count for Method " + cmid);
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Call count for Method " + cmid + " is " + counts);
		
		return counts;
	}
	
	private void bulkWrite ()
	{
		if (writeRequests.size() >= bulkUpdateCount)
		{
			if (bulkWriteThread != null && bulkWriteThread.isAlive())
			{
				try {
					bulkWriteThread.join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Creating BulkWriteThread as write size is " + writeRequests.size());
			bulkWriteThread = new BulkWriteThread(aosCollection, writeRequests, 
					writeRequestsLock);
			bulkWriteThread.start();
		}
	}
	
	public void flush ()
	{
		Controller.dcg.dumpGraph(this);
		
		if (writeRequests.size() == 0)
			return;
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Flushing with write size is " + writeRequests.size());
		
		if (bulkWriteThread == null)
			bulkWriteThread = new BulkWriteThread(aosCollection, writeRequests, 
					writeRequestsLock);
		bulkWriteThread.run();
	}
	
	public void insertDCGEntry (MemberReference callerRef, int callerLength, int callerIndex, 
			MemberReference calleeRef, int calleeLength, double weight)
	{
		DBObject m = new BasicDBObject ();
		String s = callerRef.toString();
		//s = s.replaceAll("\\{urls[^\\}]*\\}", "");
		m.put("callerRef", s);
		m.put("callerLength", callerLength);
		m.put("callerIndex", callerIndex);
		s = calleeRef.toString();
		//s = s.replaceAll("\\{urls[^\\}]*\\}", "");
		m.put("calleeRef", s);
		m.put("calleeLength", calleeLength);
		m.put("weight", weight);
		
		dcgCollection.insert(m);
	}
	
	public void readAllDocuments ()
	{
		readingCompleted = false;
		DBObject query = new BasicDBObject ("optLevel", new BasicDBObject("$ne", -1));
		DBCursor cur = aosCollection.find(query);
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Obtained cursor to the collection");
		
		while (cur.hasNext())
		{
			DBObject d = cur.next();
			String methFullDesc = (String)d.get("methodFullDesc");
			int optLevel = ((Integer)d.get("optLevel")).intValue();
			double counts = ((Double)d.get("count")).doubleValue();
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Read: " + methFullDesc + " opt level: " + optLevel + " counts: " + counts);
			internalDB.put(methFullDesc, new MethodDatabaseElement(methFullDesc, counts, optLevel));
			if (optLevel == -1)
				continue;
		}
		
		cur = dcgCollection.find();
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Start: Reading DCGCollection ");
		if (Controller.dcg == null) {
			Controller.dcg = new PartialCallGraph(300);
		} else {
			Controller.dcg.reset();  // clear any values accumulated to this point
		}
		
		if (VM.useAOSDBVerbose && Controller.dcg != null)
			VM.sysWriteln ("DCG initialized");
		
		try
		{
			while (cur.hasNext())
			{
				DBObject d = cur.next();
				String s = (String)d.get("callerRef");
				s = s.replaceAll("\\{urls[^\\}]*\\}", ""); // strip classloader cruft we can't parse
		        StringTokenizer parser = new StringTokenizer(s, " \n,");
		        
				if (VM.useAOSDBVerbose)
				{
					VM.sysWriteln ("Read DCG Entry: callerRef: " + (String)d.get("callerRef") +  
							" callerLength: " + (Integer)d.get("callerLength") +
							" callerIndex: " + (Integer)d.get("callerIndex") +
							" calleeRef: " + (String)d.get("calleeRef") +
							" calleeLength: " + (Integer)d.get("calleeLength") +
							" weight: " + (Double)d.get("weight"));
					VM.sysWriteln ("Creating DCG Entry");
				}
				
				MemberReference callerKey = MemberReference.parse(parser, false);
				if (callerKey == null) return;
				MethodReference callerRef = callerKey.asMethodReference();
				RVMMethod caller, callee;
				caller = DynamicCallFileInfoReader.getMethod(callerRef);

				int callerSize = (Integer)d.get("callerLength");
				int bci = (Integer)d.get("callerIndex");
				
				s = (String)d.get("calleeRef");
				s = s.replaceAll("\\{urls[^\\}]*\\}", ""); // strip classloader cruft we can't parse
		        parser = new StringTokenizer(s, " \n,");
				
				MemberReference calleeKey = MemberReference.parse(parser, false);
				if (calleeKey == null) return;
				MethodReference calleeRef = calleeKey.asMethodReference();
				callee = DynamicCallFileInfoReader.getMethod(calleeRef);

				int calleeSize = (Integer)d.get("calleeLength");

				float weight = (float)((Double)(d.get("weight"))).doubleValue();
				if ((caller == null) || (callee == null)) {
					Controller.dcg.incrementUnResolvedEdge(callerRef, bci, calleeRef, weight);
				} else {
					Controller.dcg.incrementEdge(caller, bci, callee, weight);
				}
				
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("DCG Entry Created");
			}
		}
		catch (Exception e)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("DCG Entry Creation Failed");
			e.printStackTrace();
		}
		
		readingCompleted = true;
		
		if (VM.useAOSDBOptBlockingCompile || VM.useAOSDBOptCompile)
		{
			for (int i = 0; i < methodsToCompileAsync.size(); i++)
			{
				MethodToCompileAsync meth = methodsToCompileAsync.get(i);
				MethodDatabaseElement elem = internalDB.get(meth.desc);
				if (elem == null)
					continue;
				
				elem.m = meth.normalMethod;
				putOptCompilationOnQueue(elem);
			}
		
		    methodsToCompileAsync.clear();
		}
		else if (VM.useAOSDBBulkCompile)
		{
			for (MethodDatabaseElement methElem : internalDB.values())
			{
				String methFullDesc = methElem.name;
				int optLevel = methElem.optLevel;

				if (optLevel == -1)
					continue;

				int o = methFullDesc.indexOf(';');
				String clsName = methFullDesc.substring(0, o+1);
				Atom at = Atom.findOrCreateUnicodeAtom(clsName);
				ClassLoader cl = RVMClassLoader.findWorkableClassloader(at);
				if (cl == null)
				{
					if (VM.useAOSDBVerbose)
						VM.sysWriteln ("Class " + clsName + " cannot be loaded");
					continue;
				}

				TypeReference tRef = TypeReference.findOrCreate(cl, at);
				RVMClass cls = (RVMClass) tRef.peekType();

				if (cls != null) {
					// Ensure the class is properly loaded
					if (!cls.isInstantiated()) {
						if (!cls.isResolved()) {
							cls.resolve();
						}

						cls.instantiate();
					}

					int r = methFullDesc.indexOf ('(');
					String methName = methFullDesc.substring(o+1, r);
					Atom methNameAtom = Atom.findOrCreateUnicodeAtom(methName);
					String methSig = methFullDesc.substring(r);
					Atom methSigAtom = Atom.findOrCreateUnicodeAtom(methSig);
					if (VM.useAOSDBVerbose)
						VM.sysWriteln ("Method " + methName + " compiling at level " + optLevel);

					RVMMethod method = cls.findDeclaredMethod(methNameAtom, methSigAtom);

					// If found, compile it
					if ((method != null) &&
							!method.hasNoOptCompileAnnotation() &&
							(method instanceof org.jikesrvm.classloader.NormalMethod)) {
						// if user's requirement is higher than advice
						CompilationPlan compPlan;
						compPlan = Controller.recompilationStrategy.createCompilationPlan((NormalMethod) method, optLevel, null);
						if (RuntimeCompiler.recompileWithOpt(compPlan) == -1 && VM.useAOSDBVerbose)
							VM.sysWriteln ("Compiling method " + methFullDesc + " failed at opt level " + optLevel); 
						else if (VM.useAOSDBVerbose)
							VM.sysWriteln ("Compiling method " + methFullDesc + " successfull at opt level " + optLevel);

					}
				} 
			}
		}
	}
		
	public void readAll ()
	{
		BulkReadThread b = new BulkReadThread (this);
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Starting BulkReadThread");
		b.start();
	}
}
