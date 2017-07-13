package network.command.server;

import Common.Gobal;
import network.bucketobject.DeleteQuery;
import network.bucketobject.Query;
import network.command.BucketCommand;

public class DeleteQueryCommand extends BucketCommand{
	
	public DeleteQuery query;
	
	public void setQuery(DeleteQuery query) {
		this.query = query;
	}
	
	public Query getQuery() {
		return query;
	}
	
	
	public void execute() {
		
		 Gobal.db.Delete(query);
	}

}
