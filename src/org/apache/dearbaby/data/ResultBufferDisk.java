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
import org.apache.dearbaby.cache.UserCacheConf;
import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.DRConstant;

public class ResultBufferDisk  extends ResultBuffer  {
	
	int rSize=0;
	protected int res[]=new int[InitConfig.MAP_FILE_HEAD_SIZE];
	String p=null;
	MapFile mf=null;
	RowsBuffer lastBuf; 
	boolean first=true;
	
	RowsBuffer readNow;
	int readNowId=-1;
	
	Date loadDate=null;
	
	RowsBuffer nRow;
	
	protected int getResSize(){
		return rSize+1;
	};
	protected RowsBuffer getRowsBuffer(int i){
		if(readNowId==i){
			return readNow;
		}
		readNowId=i;
	//	System.out.println("dddddddddddddddddddddddddddd "+i);
		if(getResSize()<i){
			return null;
		}
		int bi=res[i];
		int begin=0;
		if(i>0){
			begin=res[i-1];
		}
		byte[] b=mf.readRs(begin,bi-begin) ;
		if(readNow==null){
			readNow=new RowsBuffer();
		}
		readNow=RowsBuffer.fromSer(b,readNow);
		return readNow;
	}
	
	protected RowsBuffer getRowsBuffer0(int i){
	 
		if(getResSize()<i){
			return null;
		}
		int bi=res[i];
		int begin=0;
		if(i>0){
			begin=res[i-1];
		}
		System.out.println("restttt bi "+bi+",  b :"+begin);
		byte[] b=mf.readRs(begin,bi-begin) ;
		RowsBuffer n=new RowsBuffer();
		RowsBuffer ret= RowsBuffer.fromSer(b,n);  
		return ret;
	}
	
	protected void genFilePath(){
		Date c=new Date();
		p=InitConfig.MAP_FILE_DIR+"-"+Thread.currentThread().getId()+"-"+c.getTime()+".rt";
	}
	protected void addRowsBuffer0(RowsBuffer reb,MapFile _mf){
		 
		byte[] b=reb.toSer().array();
		int begin=0;
		
		if(rSize==0){
			begin=0;
			res[rSize]=b.length;
		}else{
			begin=res[rSize-1];
			res[rSize]=res[rSize-1]+b.length;
		}
	 
		_mf.writeRs(b, begin);
		rSize++;
		 
	}
	
	protected void addRowsBuffer(RowsBuffer reb){
		MapFile _mf=null;
		if(p==null){
			genFilePath();
			_mf=new MapFile();
			_mf.path=p;
			boolean t=_mf.open();
			if(t==false){
				return;
			}
		}else{
			_mf=mf;
		}
		addRowsBuffer0( reb, _mf);
		mf=_mf;
	}
	
	public   ResultBufferDisk(){
		 
	}
	
	
	public Map getCurrRow0 () {
		
		if (getResSize()== 0) {
			return null;
		}
		HashMap m=new HashMap();
		RowsBuffer rb=readNow;
		if(rb==null){
			rb=getRowsBuffer(0);
		}
		if(rowId>rb.end){
			for(int i=readNowId+1;i<getResSize();i++){
			
				RowsBuffer _rb=getRowsBuffer(i) ;
				if(rowId<=_rb.end){
					rb=getRowsBuffer(i) ;
					break;
				}
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
		RowsBuffer rb=readNow;
		if(rb==null){
			rb=getRowsBuffer(0);
		}
		if(rowId>rb.end){
		
			for(int i=readNowId+1;i<getResSize();i++){
			
				RowsBuffer _rb=getRowsBuffer(i) ;
				if(rowId<=_rb.end){
					rb=getRowsBuffer(i) ;
					break;
				}
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
	
	  
	  
	  
	
	
	private RowsBuffer buildRowsBuffer(){
		if(nRow!=null){
			return nRow.copy();
		}
		 
		nRow= new RowsBuffer();
		return nRow;
	}
 
	
	 
	
	public void addCol(byte[] row,int rowLeng){
		RowsBuffer rb=lastBuf;
		if(rb==null){
			rb= buildRowsBuffer();
			lastBuf=rb;
		}
		if(rb.addRow(row, rows,rowLeng)==false){
			addRowsBuffer(rb);
			RowsBuffer rb2=buildRowsBuffer();
			rb2.addRow(row, rows,rowLeng);
			lastBuf=rb2; 
			
		};
		
	}
	
 
	 
	public void addEnd(){
		if(lastBuf!=null){
			addRowsBuffer(lastBuf);
		}
		if(rSize>0){
			rSize--;
		}
		double f=(double)(res.length-rSize)/res.length;
		if( f>InitConfig.MAP_FILE_HEAD_SIZE_RE_RATIO){
			int[] res2=new int[rSize+1];
			System.arraycopy( res,0,res2,0, rSize+1);
		//	res=res2;
		}
		mf.reOpen();
	} 
	
	 

	private void fetchEnd0(){
		mf.close();
		lastBuf=null;
		readNow=null;
		readNowId=0;
				
	}
	
	public void fetchEnd(){
		super.fetchEnd();
		fetchEnd0();
				
	}
	
}
