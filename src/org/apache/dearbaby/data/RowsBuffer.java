package org.apache.dearbaby.data;

import org.apache.dearbaby.config.InitConfig;

public class RowsBuffer {

	public int begin=-1;
	public int end;
	public int bufferSize=1024*1024*10;
	public int idxSize=1000*1000;
	public byte[] buffer=new byte[bufferSize];
	public int[] indexs =new int[idxSize];
	public int idx=0;
	
	public int currIdx=0;
	
 
	
	public byte[] getRow(int rowId){
		int e=currIdx;
		if((rowId-begin)==-1){
		//	System.exit(0);
		}
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
		if(f<InitConfig.block_arrow_remain_rotio){
			return;
		}
		if(bufferSize-currIdx<InitConfig.block_arrow_remain_length){
			return;
		}
		 
		byte[] buffer2=new byte[currIdx];
		System.arraycopy(buffer, 0, buffer2, 0,currIdx);
		buffer=buffer2;
		bufferSize=currIdx;
		 
	}
	public byte[] toSer(){
		return null;
	}
	
	public RowsBuffer fromSer(byte[] buf){
		return null;
	}
}
