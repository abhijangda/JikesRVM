package org.jikesrvm.adaptive.database.methodsamples;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Semaphore;
//Run with AOS:log









import org.bson.Document;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.methodsamples.MongoMethodDatabase.WriteRequest.WriteRequestType;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoOptCompile;

import com.mongodb.Cursor;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import static com.mongodb.client.model.Filters.*;

public class MongoMethodDatabase {
	private MongoClient mongo;
	private MongoDatabase aosDatabase;
	private MongoCollection<Document> aosCollection;
	private BulkWriteThread bulkWriteThread;
	private Semaphore writeRequestsLock;
	private HashMap<String, MethodDatabaseElement> internalDB;
	private int bulkUpdateCount;

	private  List<WriteRequest> writeRequests;
	
	static class WriteRequest
	{
		public final int cmid;
		public final double count;
		public final int optLevel;
		
		enum WriteRequestType
		{
			IncrementRequest,
			PutRequest,
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
		private MongoCollection<Document> aosCollection;
		private List<WriteRequest> writeRequests;
		private Semaphore writeRequestsLock;
		
		public BulkWriteThread (MongoCollection<Document> aosCollection,
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
				
				Vector<WriteModel<Document>> writeDocuments = new Vector<WriteModel<Document>> ();

				for (WriteRequest req : _writeRequests)
				{
					if (req.type == WriteRequestType.PutRequest)
					{
						WriteModel<Document> d = putCallCount(req);
						if (d != null)
							writeDocuments.add(d);
					}
					else if (req.type == WriteRequestType.IncrementRequest)
					{
						WriteModel<Document> d = incrementCallCount (req);
						if (d != null)
							writeDocuments.add(d);
					}
				}
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Writing bulk requests");
				if (writeDocuments.size () != 0)
					aosCollection.bulkWrite(writeDocuments);

				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Wrote bulk requests");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		public WriteModel<Document> incrementCallCount (WriteRequest writeRequest)
		{
			int cmid = writeRequest.cmid;
			int optLevel = writeRequest.optLevel;
			String methodFullDesc = getMethodFullDesc(cmid);
			if (methodFullDesc == "")
				return null;
			
			double count = getCallCount (methodFullDesc);
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Method " + cmid + " with call counts " + count + " will be incremented");
			
			if (count != 0.0)
			{
				return (new UpdateOneModel <Document> (new Document ("methodFullDesc", methodFullDesc), 
					new Document ("$set", new Document ("methodFullDesc", methodFullDesc).append("count", new Double (count + 1.0)).append ("optLevel", optLevel))));
			}
			else
			{
				return (new InsertOneModel<Document> (new Document ("methodFullDesc", methodFullDesc).append ("count", new Double (1.0)).append ("optLevel", optLevel)));
			}
		}
		
		public WriteModel<Document> putCallCount (WriteRequest writeRequest)
		{
			int cmid = writeRequest.cmid;
			int optLevel = writeRequest.optLevel;
			double counts = writeRequest.count;
			String methodFullDesc = getMethodFullDesc (cmid);
			if (methodFullDesc == "")
				return null;
			
			Document meth = new Document();
			meth.put("methodFullDesc", methodFullDesc);
			meth.put("count", counts);
			meth.put("optLevel", optLevel);
			
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Put Method " + cmid + " with call counts " + counts);
			
			if (getCallCount (methodFullDesc) == 0.0)
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + cmid + " added to MongoDB");
				
				return (new InsertOneModel<Document> (meth));
			}
			else
			{
				if (VM.useAOSDBVerbose)
					VM.sysWriteln ("Method " + cmid + " updated to MongoDB");
				return (new UpdateOneModel <Document> (new Document ("methodFullDesc", methodFullDesc), 
						new Document ("$set", new Document ("methodFullDesc", methodFullDesc).append("count", counts))));
			}
		}
	}
	
	public MongoMethodDatabase (int bulkMethodCount)
	{
		this.bulkUpdateCount = bulkMethodCount;
		writeRequests = new LinkedList<WriteRequest> ();
		
		try {
			// TODO Auto-generated method stub
			mongo = new MongoClient("localhost", 27017);
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("MongoClient Initialized");

			aosDatabase = mongo.getDatabase("AOSDatabase");
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Mongo AOS Database Initialized");

			aosCollection = aosDatabase.getCollection("AOSCollection");
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Mongo AOS Collection Initialized");
			writeRequestsLock = new Semaphore (1);
			
			VM.sysWriteln ("RequestsLock created");
			internalDB = new HashMap<String, MethodDatabaseElement> ();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
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
	
	public int getOptLevelForMethod (RVMMethod m)
	{
		if (aosCollection == null)
		{
			return -1;
		}
		
		String methodFullDesc = getMethodFullDesc (m);
		if (methodFullDesc.contains("mongodb") || methodFullDesc.contains("bson") || methodFullDesc.contains("MethodDatabase") ||
				methodFullDesc.contains("Formatter") || methodFullDesc.indexOf("Ljava/") == 0 ||
				methodFullDesc.indexOf ("Lgnu/") == 0)
			return -1;
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Getting opt level for Method "+ methodFullDesc);
		
		Document meth = aosCollection.find(eq("methodFullDesc", methodFullDesc)).first();
		
		if (meth == null)
		{
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Cannot find opt level for Method " + methodFullDesc);
			
			return -1;
		}
		
		int optLevel = ((Integer)meth.get("optLevel")).intValue();
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Opt Level for Method " + methodFullDesc + " is " + optLevel);
		
		return optLevel;
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
		if (aosCollection == null && writeRequestsLock == null)
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
		
		if (writeRequests.size() >= bulkUpdateCount)
			bulkWrite ();
	}

	@Inline
	public void putCallCount(int cmid, double counts) {
		// TODO Auto-generated method stub
		//MongoDB implementation
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Put Call Count");
		
		if (aosCollection == null && writeRequestsLock == null)
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
		
		if (writeRequests.size() >= bulkUpdateCount)
			bulkWrite ();
	}
	
	private double getCallCount (String methodFullDesc)
	{
		Document meth = aosCollection.find(eq("methodFullDesc", methodFullDesc)).first();
		if (meth == null)
		{
			return 0.0;
		}
		
		return ((Double)meth.get("count")).doubleValue();
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
		if (writeRequests.size() == 0)
			return;
		
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Flushing with write size is " + writeRequests.size());
		
		bulkWriteThread = new BulkWriteThread(aosCollection, writeRequests, 
				writeRequestsLock);
		bulkWriteThread.run();
	}
	
	public void readAllDocuments ()
	{
		MongoCursor<Document> cur = aosCollection.find().iterator();
		if (VM.useAOSDBVerbose)
			VM.sysWriteln ("Obtained cursor to the collection");
		while (cur.hasNext())
		{
			Document d = cur.next();
			String methFullDesc = d.getString("methodFullDesc");
			int optLevel = d.getInteger("optLevel");
			double counts = d.getDouble("count");
			if (VM.useAOSDBVerbose)
				VM.sysWriteln ("Read: " + methFullDesc + "  " + optLevel + "  " + counts);
			internalDB.put(methFullDesc, new MethodDatabaseElement(methFullDesc, counts, optLevel));
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
