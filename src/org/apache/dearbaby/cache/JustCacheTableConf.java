package org.apache.dearbaby.cache;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.query.IExecutor;

public   class JustCacheTableConf extends CacheTableConf {

    String _sql="";
	
	public synchronized   SinResult loadCacheRule(String _sql){
		if(result==null){
			sql=_sql;
			getCache();
		}else{
			if(!sql.equalsIgnoreCase(_sql)){
				getCache();
			} 
		}
		return result.copy();
	}
	public JustCacheTableConf(String table){
		super(table,table);
	}
	public JustCacheTableConf(String table,String cacheName){
		super(table,cacheName);
	}
	 
}
