package org.apache.dearbaby.query;

import org.apache.dearbaby.cache.ExcCacheConf;

public class QuerySession {

	public ExcCacheConf cacheConf;
	 
	
	public static QuerySession jdbcSession(){
		QuerySession s=new QuerySession();
		 
		return s;
	}
	
}
