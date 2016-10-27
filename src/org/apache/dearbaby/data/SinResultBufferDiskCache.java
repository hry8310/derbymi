package org.apache.dearbaby.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.cache.CacheIndex;
import org.apache.dearbaby.cache.CacheTableConf;
import org.apache.dearbaby.cache.ResultCache;
import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.DRConstant;

public class SinResultBufferDiskCache  extends SinResultBufferDisk  {
	
	 int i=1;
	 
	
	 
	protected void genFilePath(){
		if(i==1){
			i=0;
		}else if(i==0){
			i=1;
		}
		p=InitConfig.MAP_FILE_DIR+getTableName()+"-"+Thread.currentThread().getId()+"-"+i+".mp";
	}
	 
	public   SinResultBufferDiskCache(){
		 
	}
	
	 
	protected void addRowsBuffer(RowsBuffer reb){
		MapFile _mf=null;
		genFilePath();
		_mf=new MapFile();
		_mf.path=p;
		boolean t=_mf.open();
		if(t==false){
			return;
		}
		 
		addRowsBuffer0( reb, _mf);
		mf=_mf;
	}
	
	
	public SinResultBufferDiskCache copy(){
		SinResultBufferDiskCache ret=new SinResultBufferDiskCache();
		ret.results=this.results; 
		ret.rowId=this.rowId;
		ret.endOut=this.endOut;
		ret.endSize=this.endSize;
		ret.rows=this.rows; 
		ret.isBuild=this.isBuild;
		ret.head=this.head;
		ret.dataType=this.dataType;
		ret.mf=this.mf.copy();
		ret.p=this.p;
		ret.rSize=this.rSize;
		if(this.hashIndex!=null){
			ret.hashIndex=this.hashIndex.clone();
		}
			
		return ret;
	}
	 
 
 
	
	 
	
	 
	
}
