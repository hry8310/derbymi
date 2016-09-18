package org.apache.dearbaby.data;

public class RowsBuffer {

	public int begin=-1;
	public int end;
	public byte[] buffer=new byte[1024*1024*10];
	public int[] indexs =new int[1000*1000];
	public int idx=0;
	
	public int currIdx=0;
	
 
	
	public byte[] getRow(int rowId){
		int e=currIdx;
		if((rowId-begin)==-1){
			System.out.println("begin   "+begin+" , end:  "+end+" , rowId : "+rowId+"  , idx: "+indexs[rowId+1]);
			System.exit(0);
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
}
