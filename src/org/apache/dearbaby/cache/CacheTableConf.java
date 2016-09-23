package org.apache.dearbaby.cache;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.query.IExecutor;

public abstract class CacheTableConf {

	//全部数据。
	public static int ALL=1;
	//代条件的数据
	public static int COND=2;
	
	//key
	
	public static int CKEY=3;
	private String table;
	private int type;
	private String sql;
	protected  SinResult result;
	
	public  CacheExecute executor;
	
	public String keyCol;
	
	public String getTable() {
		return table;
	}
	public void setTable(String table) {
		this.table = table;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	
	public String genSql(){
		return "select * from "+table;
	}
	
	public void getCache0(){
		String sql=genSql();
		result = executor.exe(sql);
		
	}
	
	public void getCache(){
		getCache0();
	}
	
	public abstract SinResult loadCacheRule();
	public SinResult cacheRule(){
		return loadCacheRule();
	}
}
