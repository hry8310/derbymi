package org.apache.dearbaby.cache;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.query.IExecutor;

public   class SimpCacheTableConf extends CacheTableConf {

	 
	
	public   SinResult loadCacheRule(String sql){
		if(result==null){
			getCache();
		}
		return result.copy();
	}
	public SimpCacheTableConf(String table){
		super(table,table);
	}
	public SimpCacheTableConf(String table,String cacheName){
		super(table,cacheName);
	}
	 
}
