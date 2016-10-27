package org.apache.dearbaby.cache;

import java.util.HashMap;

public  class ExcCacheConf {
	HashMap<String , UserCacheConf> h=new HashMap<String , UserCacheConf>();
	
	public void put(UserCacheConf userConf){
		 
		 
		
		h.put(userConf.table.toUpperCase(), userConf);
	}
	
	public int get(String table){
		UserCacheConf o=h.get(table.toUpperCase());
		if(o==null){
			return -1;
		}
		return o.type;
	}
	
	public UserCacheConf getConf(String table){
		return h.get(table.toUpperCase());
	}
	
	public int getIndex(String table){
		UserCacheConf o=h.get(table.toUpperCase());
		if(o==null){
			return -1;
		}
		return o.useIdx;
	}
	
	 
}
