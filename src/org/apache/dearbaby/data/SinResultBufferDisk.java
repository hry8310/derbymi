package org.apache.dearbaby.data;

import java.util.ArrayList;
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

public class SinResultBufferDisk  extends SinResultBuffer  {
	
	protected int getResSize(){
		return results.size();
	};
	private RowsBuffer getRowsBuffer(int i){
	   
		return results.get(i);
	}
	private void addRowsBuffer(RowsBuffer reb){
		results.add(reb);
	 
	}
	public Map getCurrRow0 () {
		
		if (getResSize()== 0) {
			return null;
		}
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		for(int i=bufferId;i<getResSize();i++){
			RowsBuffer _rb=getRowsBuffer(i) ;
			if(rowId<=_rb.end){
				rb=getRowsBuffer(i) ;
				break;
			}
		}
		byte[] b= rb.getRow(rowId);
		 
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), getCol(b,i));
		}
	//	return results.get(rowId);
		return m;
	}
	
	public Object getCurrCol0 (String name) {
		
		if (getResSize() == 0) {
			return null;
		}
		
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		 
		for(int i=bufferId;i<getResSize();i++){
			RowsBuffer _rb=getRowsBuffer(i) ;
		//	System.out.println("end:::  "+_rb.end+"   i  "+i);
			if(rowId<=_rb.end){
				rb=getRowsBuffer(i);
				break;
			}
		}
		byte[] b= rb.getRow(rowId);
		 
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return getCol(b,i);
			 }
		}
	 
		return null;
	}
	
	  
	  
	
	public Map getHsCurrRow () {
	//	HashBufEle ele=  (HashBufEle)hashIndex.getCurrRow();
		
	//	byte[] b=results.get(ele.bufIdx).getRow(ele.rowId);
		long ele=  (long)hashIndex.getCurrRow();
		byte[] b=getRowsBuffer(ByteUtil.getIntHght(ele)).getRow(ByteUtil.getIntLow(ele));
		
		return getColMap(b);
	}
	
	public Object getHsCurrCol (String name) {
		 
		Map m=getHsCurrRow();
	//	System.out.println("alias   name  "+name+"   "+isBuild+" ,  obj : "+m); 
		 
		Object obj= m.get(name);
	//	System.out.println("obj   "+obj+"  name  "+name); 
		return obj;
	}
	
	
	
	public SinResultBufferDisk copy(){
		SinResultBufferDisk ret=new SinResultBufferDisk();
		ret.results=this.results; 
		ret.rowId=this.rowId;
		ret.endOut=this.endOut;
		ret.endSize=this.endSize;
		ret.rows=this.rows; 
		ret.isBuild=this.isBuild;
		ret.head=this.head;
		ret.dataType=this.dataType;
		if(this.hashIndex!=null){
			ret.hashIndex=this.hashIndex.clone();
		}
			
		return ret;
	}
	 
 
 
	
	 
	
	public void addCol(byte[] row){
		RowsBuffer rb=getRowsBuffer(getResSize()-1);
	 
		if(rb.addRow(row, rows)==false){
			RowsBuffer rb2=new RowsBuffer();
			 rb2.addRow(row, rows);
			 addRowsBuffer(rb2);
			
		};
		
	}
	 
	 
	
}
