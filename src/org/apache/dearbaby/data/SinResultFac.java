package org.apache.dearbaby.data;

import org.apache.dearbaby.cache.CacheTableConf;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.util.DRConstant;

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
		SinResult sr= new SinResultBuffer();
		sr.setTableName(tableName); 
		return sr;
	}
	
	public static SinResult getSinResult1(String tableName,QueryMananger qm){
		if(tableName==null||tableName.equals("")){
			return  getSinResult(  tableName,  qm);
		}
		if(tableName.equalsIgnoreCase(qm.session.useDriverTable)){
			SinResult sr= new SinResultBufferDisk();
			sr.setTableName(tableName);
			sr.setQueryManager(qm);
			return sr;
		}
		return  getSinResult(  tableName,  qm);
	}
	
	public static SinResult getSinResult(CacheTableConf conf){
		if(conf.cacheType==DRConstant.DISKCACHE){
			SinResult sr= new SinResultBufferDiskCache();
			sr.setTableName(conf.getTable());
			return sr;
		}
		return   getSinResult(conf.getTable());
	}
	
	public static ResultBuffer getResultBuffer(int  type){
		if(type==DRConstant.RESULT_DIST){
			return new ResultBufferDisk();
		}
		if(type==DRConstant.RESULT_BUFFER){
			return new ResultBuffer();
		}
		return new ResultBufferList();
	}
}
