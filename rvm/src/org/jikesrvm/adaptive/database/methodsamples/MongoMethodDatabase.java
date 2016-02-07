package org.jikesrvm.adaptive.database.methodsamples;

import org.jikesrvm.VM;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoMethodDatabase extends MethodDatabase {
	private static MongoClient mongo;
	private static DB aosDatabase;
	private static DBCollection aosCollection;
	
	@Override
	public void initialize() {
		try {

			// TODO Auto-generated method stub
			mongo = new MongoClient("localhost", 27017);
			if (VM.verboseClassLoading)
				VM.sysWriteln ("MongoClient Initialized");

			aosDatabase = mongo.getDB("AOSDatabase");
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Mongo AOS Database Initialized");

			aosCollection = aosDatabase.getCollection("AOSCollection");
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Mongo AOS Collection Initialized");
		}
		catch (Exception e) {
			VM.sysWriteln ("ERROR");
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
		DBObject meth = new BasicDBObject();
		meth.put("cmid", cmid);
		meth.put("count", count + 1);
		
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Method " + cmid + " with call counts " + count + " will be incremented");
		
		DBObject query = new BasicDBObject ();
		query.put ("cmid", cmid);

		DBObject updateObj = new BasicDBObject ();
		updateObj.put ("$set", meth);

		if (VM.verboseClassLoading)
			VM.sysWriteln ("Method " + cmid + " incremented to MongoDB");

		aosCollection.update (query, updateObj);

	}

	@Override
	public void putCallCount(int cmid, double counts) {
		// TODO Auto-generated method stub
		//MongoDB implementation
		if (aosCollection == null)
		{
			return;
		}
		
		DBObject meth = new BasicDBObject();
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
			aosCollection.insert(meth);
		}
		else
		{
			VM.verboseClassLoading = prev;
			DBObject query = new BasicDBObject ();
			query.put ("cmid", cmid);

			DBObject updateObj = new BasicDBObject ();
			updateObj.put ("$set", meth);

			if (VM.verboseClassLoading)
				VM.sysWriteln ("Method " + cmid + " updated to MongoDB");

			aosCollection.update (query, updateObj);
		}
	}
	
	@Override
	public double getCallCount(int cmid) {
		// TODO Auto-generated method stub
		if (aosCollection == null)
		{
			return 0.0;
		}
		
		DBObject query = new BasicDBObject ("cmid", cmid);
		DBCursor cursor = aosCollection.find(query);
		if (cursor.count() == 0)
		{
			if (VM.verboseClassLoading)
				VM.sysWriteln ("Cannot find call count for Method " + cmid);
			return 0.0;
		}
		
		DBObject meth = cursor.next();
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Getting call count for Method "+ cmid);
		
		double count = ((Double)meth.get("count")).doubleValue();
		if (VM.verboseClassLoading)
			VM.sysWriteln ("Call count for Method " + cmid + " is " + count);
		
		return count;
		
	}

}
