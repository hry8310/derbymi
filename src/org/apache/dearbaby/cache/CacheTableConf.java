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
	protected String sql;
	protected  SinResult result;
	
	public  CacheExecute executor;
	
	public String keyCol;
	
	public CacheIndexList indexs=new CacheIndexList();
	
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
	public void setSql(String _sql){
		sql=_sql;
	}
	
	public void getCache0(){
		if(sql==null||sql.isEmpty()){
			sql=genSql();
		}
		
		result = executor.exe(table,sql);
		
	}
	
	public void getCache(){
		getCache0();
	}
	
	public abstract SinResult loadCacheRule(String sql);
	public SinResult cacheRule(String sql){
		return loadCacheRule(  sql);
	}
}
