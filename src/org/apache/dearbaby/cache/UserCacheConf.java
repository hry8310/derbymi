package org.apache.dearbaby.cache;

import org.apache.dearbaby.config.InitConfig;

public class UserCacheConf {
	public int type;
	public String table;
	public int useIdx;
	public int readIdx;//‘› ±≤ª”√
	public int rowRotio=InitConfig.DISK_ROW_BUFFER_SIZE_ROTIO;
	public String cacheName;
	 
	public UserCacheConf (String table){
		this.cacheName=table;
		this.table=table;
	}
	public UserCacheConf (String table,int type){
		this.table=table;
		this.type=type;
	}
	
	public UserCacheConf (String table,int type,int useIdx){
		this.cacheName=table;
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
	}
	
	public UserCacheConf (String table,int type,int useIdx,int rowRotio){
		this.cacheName=table;
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
		this.rowRotio=rowRotio;
	}
	public UserCacheConf (String table,String cacheName){
		this.cacheName=cacheName;
		this.table=table;
	}
	public UserCacheConf (String table,String cacheName,int type){
		this.table=cacheName;
		this.type=type;
	}
	
	public UserCacheConf (String table,String cacheName,int type,int useIdx){
		this.cacheName=cacheName;
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
	}
	
	public UserCacheConf (String table,String cacheName,int type,int useIdx,int rowRotio){
		this.cacheName=cacheName;
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
		this.rowRotio=rowRotio;
	}
	
}
