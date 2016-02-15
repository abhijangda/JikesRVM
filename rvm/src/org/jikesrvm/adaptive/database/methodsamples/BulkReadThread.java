package org.jikesrvm.adaptive.database.methodsamples;

public class BulkReadThread extends Thread {
	public MongoMethodDatabase mongoMethDB;
	
	public BulkReadThread (MongoMethodDatabase mongoMethDB)
	{
		this.mongoMethDB = mongoMethDB;
	}
	
	@Override
	public void run ()
	{
		mongoMethDB.readAllDocuments();
	}
}
