package org.apache.dearbaby.cache;

import org.apache.dearbaby.data.SinResult;

public interface CacheExecute {
	public SinResult exe(String table,String sql);
	
	public void setCacheTableConf(CacheTableConf conf);
	
	public CacheTableConf getCacheTableConf();
}
