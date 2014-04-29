package uk.ac.ncl.cs.csc8498.cassandra_model;

import java.text.DateFormat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class UserType {
	private static Cluster cluster;
    private static Session session;
    private static final String REGISTERED = "registered";
    private static final String UNREGISTERED = "unregistered";
    private static final String BOTS = "bots";
    private static final String IPADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
    												"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
    												"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
    												"([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    public UserType()
    { 
    	
    	 cluster = new Cluster.Builder().addContactPoint("127.0.0.1").build(); 
		 final int numberOfConnections = 1;
		 PoolingOptions poolingOptions = cluster.getConfiguration().getPoolingOptions();
		 poolingOptions.setCoreConnectionsPerHost(HostDistance.LOCAL, numberOfConnections);
		 poolingOptions.setMaxConnectionsPerHost(HostDistance.LOCAL, numberOfConnections);
		 poolingOptions.setCoreConnectionsPerHost(HostDistance.REMOTE, numberOfConnections);
		 poolingOptions.setMaxConnectionsPerHost(HostDistance.REMOTE, numberOfConnections);
		 final Session bootstrapSession = cluster.connect();
		 bootstrapSession.execute("CREATE KEYSPACE IF NOT EXISTS wikiproject WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };");
		 bootstrapSession.shutdown();	
		 session = cluster.connect("wikiproject");
		 session.execute("CREATE TABLE IF NOT EXISTS user_type (type text, hits counter, PRIMARY KEY (type));");	
    }
    
    /**
	 * create table with 'type' column as the primary key, and count column to
	 * show number of times each page type has been edited.
	 * 
	 * @throws InterruptedException
	 */
    public void writeToDB() throws InterruptedException {
		//String psString = "SELECT user, hits FROM user_edits_per_hour;";
		String psString = "SELECT user FROM user_edit;";
		final int maxOutstandingFutures = 4;
		final BlockingQueue<ResultSetFuture> outstandingFutures = new LinkedBlockingQueue<>(
				maxOutstandingFutures);
		// prepared statement for inserting records into the table
		final PreparedStatement updatePS = session
				.prepare("UPDATE user_type SET hits = hits + ? WHERE type = ?;");

		String user = "";
		String type = "";
		long hits = 0L;
	
		// iterate through the result set and print the results on the console
		final ResultSetFuture queryFuture = session.executeAsync(psString);
		ResultSet resultSet = queryFuture.getUninterruptibly();
		int count = 0;
		for (Row row : resultSet) {
			user = row.getString(0);
			//hits = row.getLong(1);
			if (user.toLowerCase().contains("bot"))
			{
				type = BOTS;
			}else if (user.matches(IPADDRESS_PATTERN))
			{
				type = UNREGISTERED;
			}else
			{
				type = REGISTERED;
			}
			
			BoundStatement boundState = new BoundStatement(updatePS).bind(1L,
					type);
			System.out.println(count++);

			// when the batch is full, execute asynchronously
			outstandingFutures.put(session.executeAsync(boundState));
			if (outstandingFutures.remainingCapacity() == 0) {
				ResultSetFuture resultSetFuture = outstandingFutures.take();
				resultSetFuture.getUninterruptibly();
			}
		}
		while (!outstandingFutures.isEmpty()) {
			ResultSetFuture resultSetFuture = outstandingFutures.take();
			resultSetFuture.getUninterruptibly();
		}
		cleanup();
	}
    
    /**
     * test page types
     */
    public void testUserType()
    {
    	String psString = "SELECT type, hits FROM user_type WHERE type IN ('registered', 'bots', 'unregistered')";
    	// iterate through the result set and print the results on the console
   		final ResultSetFuture queryFuture = session.executeAsync(psString);	
   		ResultSet resultSet = queryFuture.getUninterruptibly();	
   		for (Row row : resultSet)
   		{
   			System.out.println(row.getString(0) + " : " + row.getLong(1));
   		}
   		cleanup();
    }
    
    public void cleanup() {
        session.shutdown();
        cluster.shutdown();
    }


	public static void main(String[] args) {
		UserType ut = new UserType();
		try {
			ut.writeToDB();
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//ut.testUserType();

	}

}
