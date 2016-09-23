package org.apache.dearbaby.cache;

import java.util.HashMap;

public  class ExcCacheConf {
	HashMap h=new HashMap();
	public void put(String table,int type){
		h.put(table.toUpperCase(), type);
	}
	
	public int get(String table){
		Object o=h.get(table.toUpperCase());
		if(o==null){
			return -1;
		}
		return (int)o;
	}
}
