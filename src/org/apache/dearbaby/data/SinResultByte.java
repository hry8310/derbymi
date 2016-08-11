package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;

public class SinResultByte  {
	private int rowId = 0;
	private boolean endOut = false;
//	private ArrayList<Map> results = new ArrayList<Map>();
	public ArrayList<Object[]> results = new ArrayList<Object[]>();
	
	private ArrayList<byte[]> results2 = new ArrayList<byte[]>();
	
	private ArrayList<String > head=null;
	private int[] dataType=null;
	private int rowSize=20;
	private int rows=0;
	
	private int drvRowId=0;
	private int endSize=0;
	
	public void drv(int rowid,int end){
		drvRowId=rowid;
		rows=end;
		rowId=rowid;
	}
	
	public int endSize(){
		return endSize;
	}
	
	
	
	public Map getCurrRow () {
		
		if (results.size() == 0) {
			return null;
		}
		HashMap m=new HashMap();
		Object[] obj=results.get(rowId);
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), obj[i]);
		}
	//	return results.get(rowId);
		return m;
	}
	
	public Object getCurrCol (String name) {
		if (results.size() == 0) {
			return null;
		}
		Object[] obj=results.get(rowId);
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return obj[i];
			 }
		}
	//	return results.get(rowId);
		return null;
	}
	
	public Map getCurrRow_byte() {
		
		if (rows== 0) {
			return null;
		}
		HashMap m=new HashMap();
		byte[] obj=results2.get(rowId);
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), getCol(obj,i));
		}
		return m;
	}
	
	
	public Object getCurrCol_byte(String name) {
		if (rows== 0) {
			return null;
		}
		byte[] obj=results2.get(rowId);
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return getCol(obj,i);
			 }
		}
	//	return results.get(rowId);
		return null;
	}
	
	public Map nextRow() {
		rowId++;
/*
		if (rowId > results2.size() - 1) {
			endOut = true;
			rowId = results2.size() - 1;
		}
*/
		if (rowId > rows - 1) {
			endOut = true;
			rowId = rows - 1;
		}
		return getCurrRow();
	}
	
	public void nextTo() {
		rowId++;
		//System.out.println("rows : "+rows+"  , rowId : "+rowId);
		if (rowId > rows - 1) {
			endOut = true;
			rowId = rows - 1;
		}
		 ;
	}
	
	public void init() {
		rowId = drvRowId;
		endOut = false;
	}
	
	public boolean isEnd() {
		//return rowId >= results2.size() - 1;
		return rowId>=rows-1;
	}

	public boolean isEndOut() {
		return endOut;
	}
	
	public void add(Map m){
		if(m==null){
			return;
		}
		int needType=0;
		if(head==null){
			head=new ArrayList<String>();
			
			Iterator iter = m.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = entry.getKey().toString();
				head.add(key);
			}
			needType=1;
		}
		Object obj[]=new Object[head.size()];
		if(dataType==null){
			dataType=new int[head.size()];
		}
		byte cos[]=new byte[head.size()*4+rowSize];
		
		for(int i=0;i<head.size();i++){
			obj[i]=m.get(head.get(i));
			cos=putCol(cos,m.get(head.get(i)),i);
			if(needType==1){
				dataType[i]=ColCompare.getColType(obj[i]);
			}
		}
		rows++;
		endSize++;
		results.add(obj);
		results2.add(cos);
	}
	
	public void add_ok(Map m){
		if(m==null){
			return;
		}
		int needType=0;
		if(head==null){
			head=new ArrayList<String>();
			
			Iterator iter = m.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = entry.getKey().toString();
				head.add(key);
			}
			needType=1;
		}
		Object obj[]=new Object[head.size()];
		if(dataType==null){
			dataType=new int[head.size()];
		}
		byte cos[]=new byte[head.size()*4+100];
		
		for(int i=0;i<head.size();i++){
			obj[i]=m.get(head.get(i));
			cos=putCol(cos,m.get(head.get(i)),i);
			if(needType==1){
				dataType[i]=ColCompare.getColType(obj[i]);
			}
		}
		results.add(obj);
		results2.add(cos);
	}
	
	public SinResultByte clone(){
		SinResultByte ret=new SinResultByte();
		ret.results=this.results;
		ret.results2=this.results2;
		ret.rowId=this.rowId;
		ret.endOut=this.endOut;
		ret.endSize=this.endSize;
		ret.rows=this.rows;
		ret.head=this.head;
		return ret;
	}
	
	public int size(){
		//return results2.size();
		return endSize;
	}
	
	private byte[] putCol(byte[] buf,Object o ,int i){
		int pre=0;
		int hSize=head.size();
		if(i-1>=0){
			pre=ByteUtil.byte2int(buf,(i-1)*4);
		}
		byte[] b=o.toString().getBytes();
		if(buf.length-(head.size()*4+pre)<b.length){
			byte[ ] tmp;
			if(i==hSize-1){
				 tmp=new byte[head.size()*4+pre+b.length];
				// tmp=new byte[buf.length+b.length];
			}else{
				 tmp=new byte[buf.length+b.length];
			}
			System.arraycopy(buf, 0,tmp , 0, buf.length);
			buf=tmp;
		}
		if(i==hSize-1){
			rowSize=pre+b.length;
		}
		ByteUtil.intCopybyte(pre+b.length, buf, i*4);
		System.arraycopy( b,0,buf,head.size()*4+pre,b.length);
		return buf;
	}
	
	private Object getCol(byte[] buf,int i){
		int pre=0;
		if(i-1>=0){
			pre=ByteUtil.byte2int(buf,(i-1)*4);
		}
		int ts=ByteUtil.byte2int(buf,i*4);
		byte[] b=new byte[ts-pre];
	//	System.out.println("pre  "+pre+"  pos :  "+(head.size()*4+pre) +",  buf : "+buf.length +"  leng "+(ts-pre));
		System.arraycopy(buf, head.size()*4+pre, b, 0, ts-pre);
		Object obj=null;
		obj= ByteUtil.getCol(b,dataType[i]);
	 
		return obj;
			
	}
}
