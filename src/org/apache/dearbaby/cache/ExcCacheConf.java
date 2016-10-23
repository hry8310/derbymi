package org.apache.dearbaby.cache;

import java.util.HashMap;

public  class ExcCacheConf {
	HashMap<String , CacheConf> h=new HashMap<String , CacheConf>();
	public void put(String table,int type,int useIdx){
		CacheConf c=h.get(table);
		if(c==null){
			c=new CacheConf();
			
		}
		c.type=type;
		c.useIdx=useIdx;
		c.table=table;
		
		h.put(table.toUpperCase(), c);
	}
	public void put(String table,int type){
		put(table,type,0);
	}
	
	public int get(String table){
		CacheConf o=h.get(table.toUpperCase());
		if(o==null){
			return -1;
		}
		return o.type;
	}
	
	public int getIndex(String table){
		CacheConf o=h.get(table.toUpperCase());
		if(o==null){
			return -1;
		}
		return o.useIdx;
	}
	
	public class CacheConf{
		public int type;
		public String table;
		public int useIdx;
		public int readIdx;//‘› ±≤ª”√
	}
}
