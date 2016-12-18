package org.apache.dearbaby.data;

import org.apache.dearbaby.query.QueryMananger;

public abstract class AbstractSinResult implements SinResult {
	public QueryMananger qm;
	public String tableName;
	public ResultRef ref;
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
		if(ref!=null){
			ref.unRef();
		}
	}
	
	public boolean clear(){
		return true;
	}
	//empt
	public Object getColVal(long l,String name){
		return null;
	}
	public Object getColVal(long l,int headerId){
		return null;
	}
	public int getHeadId(String name){
		return -1;
	}
}
