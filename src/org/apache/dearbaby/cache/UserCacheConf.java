package org.apache.dearbaby.cache;

import org.apache.dearbaby.config.InitConfig;

public class UserCacheConf {
	public int type;
	public String table;
	public int useIdx;
	public int readIdx;//‘› ±≤ª”√
	public int rowRotio=InitConfig.DISK_ROW_BUFFER_SIZE_ROTIO;
	
	 
	public UserCacheConf (String table){
		this.table=table;
	}
	public UserCacheConf (String table,int type){
		this.table=table;
		this.type=type;
	}
	
	public UserCacheConf (String table,int type,int useIdx){
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
	}
	
	public UserCacheConf (String table,int type,int useIdx,int rowRotio){
		this.table=table;
		this.type=type;
		this.useIdx=useIdx;
		this.rowRotio=rowRotio;
	}
	
}
