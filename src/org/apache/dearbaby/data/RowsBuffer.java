package org.apache.dearbaby.data;

import java.nio.ByteBuffer;

import org.apache.dearbaby.config.InitConfig;
import org.apache.dearbaby.mem.RowBufferPool;

import sun.nio.ch.DirectBuffer;

public class RowsBuffer {
	public int begin=-1;
	public int end;
	public int bufferSize=InitConfig.ROWS_BUFFER_SIZE;
	public int idxSize=InitConfig.ROWS_SIZE;
	public ByteBuffer buffer;
	public int[] indexs;
	public int idx=0;
	
	public int currIdx=0;
	
	public RowsBuffer(){
	//     buffer=ByteBuffer.allocateDirect(bufferSize);
		 buffer=RowBufferPool.getPool().allocate(bufferSize);
	     indexs =new int[idxSize];
	}
	
 
	public RowsBuffer(int radio){
		//idxSize=idxSize*radio;
	     //buffer=ByteBuffer.allocateDirect(bufferSize/radio);

		 buffer=RowBufferPool.getPool().allocate(bufferSize);
		 indexs =new int[idxSize];
	}
	 
	public RowsBuffer(ByteBuffer _bf,int[] idx){
	     buffer=_bf;
		 indexs =idx;
	}
	 
	byte[] ret;
	public byte[] getRow(int rowId){
		int e=currIdx;
		if((rowId-begin)==-1){
		//	System.exit(0);
		}

	//	System.out.println("eeeeeeeeeeeeeeeeeee "+rowId);
		if(rowId<end){
			e=indexs[(rowId-begin)+1];
		}
	    int b=indexs[(rowId-begin)];
	    if(ret==null||ret.length<e-b){
	    	 ret=new byte[e-b+5] ;
	    }
	    buffer.position(b);
		   
	    buffer.get(ret, 0, e-b);
	 //   System.out.println("eeeeeeeeeeeeeeeeeee "+new String(ret));
		return ret;
	}
	
	public boolean addRow(byte[] row,int rowId,int rowLength){
		if(idx>=idxSize-1){
			//计算
			int i=(buffer.capacity()-currIdx)/rowLength;
			//已经无法保证能再放一条了，直接返回full
			if(i<=1){
				return false;
			}
			int idxSizeTmp=idxSize+i+5;
			int[] indexsTmp =new int[idxSizeTmp];
			System.arraycopy(indexs, 0, indexsTmp, 0, idxSize);
			idxSize=idxSizeTmp;
			indexs=indexsTmp;
		}
		if((buffer.capacity()-currIdx)<rowLength){
			int[] indexsTmp =new int[idx];
			System.arraycopy(indexs, 0, indexsTmp, 0, indexsTmp.length);
			indexs=indexsTmp;
			return false;
		}
		
		if(begin==-1){
			begin=rowId;
		}
		end=rowId;
	//	System.out.println("end...................  "+end);
		//System.arraycopy(row, 0, buffer, currIdx, rowLength);
		buffer.position(currIdx);
		buffer.put(row,0,rowLength);
		 
		indexs[idx]=currIdx;
		idx=idx+1;
		currIdx=currIdx+rowLength;
		return true;
	}
	
	
	public void compress(){
		double f=((double)(bufferSize-currIdx))/bufferSize;
		if(f<InitConfig.BLOCK_ARROW_REMAIN_ROTIO){
			return;
		}
		if(bufferSize-currIdx<InitConfig.BLOCK_ARROW_REMAIN_LENGTH){
			return;
		}
		 
		byte[] buffer2=new byte[currIdx];
//		System.arraycopy(buffer, 0, buffer2, 0,currIdx);
	//	buffer=buffer2;
//		bufferSize=currIdx;
		 
	}
	ByteBuffer buf=null; 
	public ByteBuffer toSer(){
		int bi=buffer.capacity();
		int ii=indexs.length;
		int toLen=20+bi+ii*4;
		int bufLen=20+bi+ii*4;
		if(buf==null||buf.capacity()<bufLen){
			buf= ByteBuffer.allocate(bufLen); 
		}else{
			buf.clear();
		}
		//ByteBuffer buf = ByteBuffer.allocate(20+bi+ii*4);
		 

		buf.putInt(begin);
		buf.putInt(end);
		buf.putInt(currIdx);
		buf.putInt(bi);
		buf.putInt(ii);
		byte[] bf=new byte[bi];
		buffer.position(0);
		buffer.get(bf);
		buf.put(bf);
		//buf.put(buffer);
		for(int i=0;i<ii;i++){
			
			buf.putInt(indexs[i]);
		}
		return buf;
	}
	
	public static RowsBuffer    fromSer( byte[] b  ,RowsBuffer n){ 
		ByteBuffer buf =ByteBuffer.wrap(b);
	    
		
		n.begin=buf.getInt();
		n.end=buf.getInt();
		n.currIdx=buf.getInt();
		int bi=buf.getInt();
		int ii=buf.getInt();
		//n.buffer=new byte[bi];
		if(n.buffer.capacity()<bi){
			RowBufferPool.getPool().recycle(n.buffer);
			n.buffer=RowBufferPool.getPool().allocate(bi);
		}else{
			n.buffer.clear();
		}
		
		n.indexs=new int[ii];
 
		try{
	 
			n.buffer.put(b,20,bi);
		}catch(Exception e)
		{
			System.out.println("n.......... "+n.buffer.capacity()+"   , "+bi);
			
		}
		buf.position(20+bi);
		//System.out.println("buffer ;;....   "+new String(n.buffer.array()));
		for(int i=0;i<ii;i++){
			n.indexs[i]=buf.getInt();
		}
		return n;
	}
	
	public RowsBuffer copy(){
		buffer.clear();
		return new RowsBuffer(buffer,indexs);
	}
	
	public void clear(){
		RowBufferPool.getPool().recycle(buffer);
		buffer=null;
	}
}
