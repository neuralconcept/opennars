package moriarty.wi.experimental.opennars;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.server.WrappingNeoServer;
import org.neo4j.server.WrappingNeoServerBootstrapper;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;

public class TestGraphs {
	static GraphDatabaseService graphDb;
	static Node firstNode;
	static Node secondNode;
	static Relationship relationship;
	
	 public static void main(String[] args){
		 connectAndStartBootstrapper();
		 
		 try ( Transaction tx = graphDb.beginTx() )
		 {
			 firstNode = graphDb.createNode();
			 firstNode.setProperty( "message", "Hello, " );
			 secondNode = graphDb.createNode();
			 secondNode.setProperty( "message", "World!" );

			 relationship = firstNode.createRelationshipTo( secondNode,new RelationshipType() {
				
				@Override
				public String name() {
					// TODO Auto-generated method stub
					return "KNOWS";
				}
			} );
			 relationship.setProperty( "message", "brave Neo4j " );
		     // Database operations go here
		     tx.success();
		 }
		 
	 }
		
	

	@SuppressWarnings("deprecation")
	public static void connectAndStartBootstrapper() {
	    WrappingNeoServerBootstrapper neoServerBootstrapper;
	     graphDb = new GraphDatabaseFactory()
	            .newEmbeddedDatabaseBuilder("/path/to/db").newGraphDatabase();

	    try {
	        GraphDatabaseAPI api = (GraphDatabaseAPI) graphDb;

	        ServerConfigurator config = new ServerConfigurator(api);
	        config.configuration()
	            .addProperty(Configurator.WEBSERVER_ADDRESS_PROPERTY_KEY, "127.0.0.1");
	        config.configuration()
	            .addProperty(Configurator.WEBSERVER_PORT_PROPERTY_KEY, "7575");

	        neoServerBootstrapper = new WrappingNeoServerBootstrapper(api, config);
	        neoServerBootstrapper.start();
	    }catch(Exception e) {
	       //handle appropriately
	    }
	}
}

