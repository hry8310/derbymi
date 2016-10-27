package org.apache.dearbaby.data;

import java.nio.ByteBuffer;

import org.apache.dearbaby.config.InitConfig;

public class RowsBuffer {

	public int begin=-1;
	public int end;
	public int bufferSize=InitConfig.ROWS_BUFFER_SIZE;
	public int idxSize=InitConfig.ROWS_SIZE;
	public byte[] buffer;
	public int[] indexs;
	public int idx=0;
	
	public int currIdx=0;
	
	public RowsBuffer(){
	     buffer=new byte[bufferSize];
		 indexs =new int[idxSize];
	}
	
	public RowsBuffer(int radio){
	     buffer=new byte[bufferSize/radio];
		 indexs =new int[idxSize/radio];
	}
	
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
		byte[] ret=new byte[e-b] ;
		System.arraycopy(buffer, b, ret, 0, e-b);
		return ret;
	}
	
	public boolean addRow(byte[] row,int rowId){
		if(idx>=idxSize){
			//计算
			int i=(buffer.length-currIdx)/row.length;
			//已经无法保证能再放一条了，直接返回full
			if(i<=1){
				return false;
			}
			int idxSizeTmp=idxSize+i+5;
			int[] indexsTmp =new int[idxSizeTmp];
			System.arraycopy(indexs, 0, indexsTmp, 0, indexsTmp.length);
			idxSize=idxSizeTmp;
			indexs=indexsTmp;
		}
		if((buffer.length-currIdx)<row.length){
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
		System.arraycopy(row, 0, buffer, currIdx, row.length);
		
		 
		indexs[idx]=currIdx;
		idx=idx+1;
		currIdx=currIdx+row.length;
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
		System.arraycopy(buffer, 0, buffer2, 0,currIdx);
		buffer=buffer2;
		bufferSize=currIdx;
		 
	}
	public ByteBuffer toSer(){
		int bi=buffer.length;
		int ii=indexs.length;
		ByteBuffer buf = ByteBuffer.allocate(20+bi+ii*4);

		buf.putInt(begin);
		buf.putInt(end);
		buf.putInt(currIdx);
		buf.putInt(bi);
		buf.putInt(ii);
		buf.put(buffer);
		for(int i=0;i<ii;i++){
			
			buf.putInt(indexs[i]);
		}
		return buf;
	}
	
	public static RowsBuffer fromSer( byte[] b ){ 
		ByteBuffer buf =ByteBuffer.wrap(b);
	    
		RowsBuffer n=new RowsBuffer();
		n.begin=buf.getInt();
		n.end=buf.getInt();
		n.currIdx=buf.getInt();
		int bi=buf.getInt();
		int ii=buf.getInt();
		n.buffer=new byte[bi];
		n.indexs=new int[ii];
		buf.get(n.buffer);
	//	System.out.println("buffer ;;....   "+new String(n.buffer));
		for(int i=0;i<ii;i++){
			n.indexs[i]=buf.getInt();
		}
		return n;
	}
}
