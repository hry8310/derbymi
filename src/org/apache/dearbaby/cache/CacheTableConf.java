package org.apache.dearbaby.cache;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.mem.RowBufferPool;
import org.apache.dearbaby.query.IExecutor;
import org.apache.dearbaby.util.DRConstant;

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
	protected  SinResult result=null;
	
	public  CacheExecute executor;
	
	public String keyCol;
	
	public CacheIndexList indexs=new CacheIndexList();
	
	public int cacheType=DRConstant.MEMCACHE;
	
	private String cacheName;
	public CacheTableConf(String _table,String _cacheName){
		table=_table;
		cacheName=_cacheName;
	}
	
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
	
	private void getCache0(){
		if(sql==null||sql.isEmpty()){
			sql=genSql();
		}
		
		result = executor.exe(table,sql);
		
	}
	
	public final void getCache(){
		if(result!=null){
			return ;
		}
		getCache0();
	}
	
	public abstract SinResult loadCacheRule(String sql);
	public SinResult cacheRule(String sql){
		SinResult s= loadCacheRule(  sql);
		endCacheLoad(s);
		return s;
	}
	protected void endCacheLoad(SinResult s){
		RowBufferPool.getPool().chkNotify();
	}
	
	public int getCacheType() {
		return cacheType;
	}
	public void setCacheType(int cacheType) {
		this.cacheType = cacheType;
	}
	public String getCacheName() {
		return cacheName;
	}
	public void setCacheName(String cacheName) {
		this.cacheName = cacheName;
	}
	
	
}
