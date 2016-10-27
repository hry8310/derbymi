package org.apache.dearbaby.data;

import org.apache.dearbaby.query.QueryMananger;

public abstract class AbstractSinResult implements SinResult {
	public QueryMananger qm;
	public String tableName;
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public void setQueryManager( QueryMananger qm){
		this.qm=qm;
	}
	public QueryMananger getQueryMananger(){
		return qm;
	}

	public void fetchEnd(){
		
	}
	
}
