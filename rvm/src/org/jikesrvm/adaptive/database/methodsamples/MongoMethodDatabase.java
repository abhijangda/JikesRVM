package org.jikesrvm.adaptive.database.methodsamples;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Semaphore;

import org.bson.Document;
import org.jikesrvm.VM;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

import static com.mongodb.client.model.Filters.*;

public class MongoMethodDatabase extends MethodDatabase {
	private MongoClient mongo;
	private MongoDatabase aosDatabase;
	private MongoCollection<Document> aosCollection;
	private Vector<WriteModel<Document>> writeRequests;
	private BulkWriteThread bulkWriteThread;
	private Semaphore writeRequestsLock;
	
	class BulkWriteThread extends Thread
	{
		private MongoCollection<Document> aosCollection;
		private Vector<WriteModel<Document>> writeRequests;
		private Semaphore writeRequestsLock;
		
		public BulkWriteThread (MongoCollection<Document> aosCollection,
				Vector<WriteModel<Document>> writeRequests, Semaphore writeRequestsLock)
		{
			this.writeRequestsLock = writeRequestsLock;
			this.aosCollection = aosCollection;
			this.writeRequests = writeRequests;
		}
		
		public void run ()
		{
			try {
				writeRequestsLock.acquire();
				if (VM.verboseClassLoading)
					VM.sysWriteln ("Writing bulk requests");
				if (writeRequests.size () != 0)
					aosCollection.bulkWrite(writeRequests);

				if (VM.verboseClassLoading)
					VM.sysWriteln ("Wrote bulk requests");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			writeRequests.clear();
			writeRequestsLock.release();
		}
	}
	
	public MongoMethodDatabase (int bulkMethodCount)
	{
		super (bulkMethodCount);
		
		writeRequests = new Vector<WriteModel<Document>> ();
	}
	
	@Override
	public void initialize() {
		try {
			// TODO Auto-generated method stub
			mongo = new MongoClient("localhost", 27017);
			if (VM.verboseClassLoading)
				VM.sysWriteln ("MongoClient Initialized");

			aosDatabase = mongo.getDatabase("AOSDatabase");
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Mongo AOS Database Initialized");

			aosCollection = aosDatabase.getCollection("AOSCollection");
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Mongo AOS Collection Initialized");
			writeRequestsLock = new Semaphore (1);
			VM.sysWriteln ("RequestsLock created");
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	public void incrementCallCount(int cmid) {
		// TODO Auto-generated method stub
		
		if (aosCollection == null)
		{
			return;
		}
		
		boolean prev = VM.verboseClassLoading;
		VM.verboseClassLoading = false;
		double count = getCallCount (cmid);
		VM.verboseClassLoading = prev;
		
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Method " + cmid + " with call counts " + count + " will be incremented");
		try {
			writeRequestsLock.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (count != 0.0)
		{
			writeRequests.add(new UpdateOneModel <Document> (new Document ("cmid", cmid), 
				new Document ("$set", new Document ("cmid", cmid).append("count", new Double (count + 1.0)))));
		}
		else
		{
			writeRequests.add(new InsertOneModel<Document> (new Document ("cmid", cmid).append ("count", new Double (1.0))));
		}
		writeRequestsLock.release();
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Method " + cmid + " incremented to MongoDB");
		
		bulkWrite ();
	}

	@Override
	public void putCallCount(int cmid, double counts) {
		// TODO Auto-generated method stub
		//MongoDB implementation
		if (aosCollection == null)
		{
			return;
		}
		
		Document meth = new Document();
		meth.put("cmid", cmid);
		meth.put("count", counts);

		if (VM.verboseClassLoading)
			VM.sysWriteln ("Put Method " + cmid + " with call counts " + counts);
		boolean prev = VM.verboseClassLoading;
		VM.verboseClassLoading = false;
		if (getCallCount (cmid) == 0.0)
		{
			VM.verboseClassLoading = prev;
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Method " + cmid + " added to MongoDB");
			try {
				writeRequestsLock.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			writeRequests.add(new InsertOneModel<Document> (meth));
			writeRequestsLock.release();
		}
		else
		{
			VM.verboseClassLoading = prev;

			if (VM.verboseClassLoading)
				VM.sysWriteln ("Method " + cmid + " updated to MongoDB");
			try {
				writeRequestsLock.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			writeRequests.add(new UpdateOneModel <Document> (new Document ("cmid", cmid), 
					new Document ("$set", new Document ("cmid", cmid).append("count", counts))));
			writeRequestsLock.release();
		}
		bulkWrite ();
	}
	
	@Override
	public double getCallCount(int cmid) {
		// TODO Auto-generated method stub
		if (aosCollection == null)
		{
			return 0.0;
		}
		
		Document meth = aosCollection.find(eq("cmid", cmid)).first();
		if (meth == null)
		{
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Cannot find call count for Method " + cmid);
			return 0.0;
		}
		
		/*if (!(meth.get("count") instanceof Double))
		{
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Cannot find call count for Method " + cmid);
			return 0.0;
		}*/
		
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Getting call count for Method "+ cmid);
		
		double count = ((Double)meth.get("count")).doubleValue();
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Call count for Method " + cmid + " is " + count);
		
		return count;
		
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
			bulkWriteThread = new BulkWriteThread(aosCollection, writeRequests, 
					writeRequestsLock);
			bulkWriteThread.start();
		}
	}
}
