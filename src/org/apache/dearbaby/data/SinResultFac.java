package org.apache.dearbaby.data;

import org.apache.dearbaby.query.QueryMananger;

public class SinResultFac {

	public static SinResult getSinResult(String tableName,QueryMananger qm){
		//return new SinResultMap();
		SinResult sr= new SinResultBuffer();
		sr.setTableName(tableName);
		sr.setQueryManager(qm);
		return sr;
	}
	public static SinResult getSinResult(){
		return  new SinResultBuffer();
	}
	
	public static SinResult getSinResult(String tableName){
		return  new SinResultBuffer();
	}
	
	public static SinResult getSinResult1(String tableName,QueryMananger qm){
		if(tableName==null||tableName.equals("")){
			return  getSinResult(  tableName,  qm);
		}
		if(tableName.equalsIgnoreCase(qm.useDriverTable)){
			return new SinResultBufferDisk();
		}
		return  getSinResult(  tableName,  qm);
	}
}
