package org.apache.dearbaby.cache;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.dearbaby.data.SinResult;

public  class ResultCache {
	private static HashMap<String,CacheTableConf > tables=new HashMap<String,CacheTableConf >();
	public static void addTable(CacheTableConf table){
		tables.put(table.getCacheName().toUpperCase(),table);
	}

	public static CacheTableConf findTable(String cacheName){
		return tables.get(cacheName.toUpperCase());
	}
	
	public static boolean needCache(String table){
		CacheTableConf c=findTable(table);
		if(c==null){
			return false;
		}
		return true;
	}
	
	public static  boolean isKey(String table,String key){
		CacheTableConf c=findTable(table);
		if(c==null){
			return false;
		}
		if(c.keyCol==null){
			return false;
		}
		if(c.keyCol.equalsIgnoreCase(key)){
			return true;
		}
		return false;
	}
	
	public static SinResult getResult(String table,String sql){
		CacheTableConf c=findTable(table);
		if(c==null){
			return null;
		}
		return c.cacheRule(sql);
	}
	
	
}
