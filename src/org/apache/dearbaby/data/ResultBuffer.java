package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.sj.ResultMap;
import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.QueryUtil;

public class ResultBuffer {
	protected int rowId = 0;
	protected int bufferId=0;
	protected boolean endOut = false;
	public ArrayList<RowsBuffer> results = new ArrayList<RowsBuffer>();
	

	protected ArrayList<String > head=null;
	protected int[] dataType=null;
	protected int rowSize=20;
	protected int rows=0;
	
	protected int drvRowId=0;
	protected int endSize=0;
	 
	
	public ResultBuffer(){
		 
		RowsBuffer rb=new RowsBuffer();
		results.add(rb);
		
	}
	public void drv(int rowid,int end){
		drvRowId=rowid;
		rows=end;
		rowId=rowid;
	}
	
	public int endSize(){
		return endSize;
	}
	
	
	
	public Map getCurrRow0 () {
		
		if (results.size() == 0) {
			return null;
		}
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		for(int i=bufferId;i<results.size();i++){
			RowsBuffer _rb=results.get(i);
			if(rowId<=_rb.end){
				rb=results.get(i);
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
		
		if (results.size() == 0) {
			return null;
		}
		
		HashMap m=new HashMap();
		RowsBuffer rb=null;
		 
		for(int i=bufferId;i<results.size();i++){
			RowsBuffer _rb=results.get(i);
		//	System.out.println("end:::  "+_rb.end+"   i  "+i);
			if(rowId<=_rb.end){
				rb=results.get(i);
				break;
			}
		}
		byte[] b= rb.getRow(rowId);
		 
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equalsIgnoreCase(name)){
				 return getCol(b,i);
			 }
		}
	 
		return null;
	}
	
	public Map getCurrRow () {
		 
		return getCurrRow0();
	}
	
	public  Object getAggrCurrCol (String name) {
		 
		String iName=QueryUtil.getColName(name);
		return getCurrCol0(iName);
	}
	
	public  Object getCurrCol (String name) {
		 
		return getCurrCol0(name);
	}
	
	public  Object getCurrCol (String alias,String name) {
		String iName=QueryUtil.getColName(alias,name);
		return getCurrCol0(iName);
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
	
	public void again() {
		rowId = drvRowId;
	}
	
	public boolean hasNext() {
		return rowId<rows-1;
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
		addCol(cos,cos.length);
		
		rows=rows+1;
		endSize++;
		 
	}
	
	public void add(ResultBuffer rb){
		while(rb.endOut==false){
			Map m=rb.getCurrRow();
			add(m);
			rb.nextTo();
		}
	}
	
	
	public void comp(ResultBuffer rb){
		if(results.size()==0){
			results.addAll(rb.results);
		}else{
			RowsBuffer last=results.get(results.size()-1);
			for(RowsBuffer one: rb.results){
				one.begin=one.begin+last.end;
				one.end=one.end+last.end;
			}
			results.addAll(rb.results);
		}
		
		endSize=endSize+rb.endSize;
		
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
	
	protected Object getCol(byte[] buf,int i){
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
	
	public Object getCol(byte[] b,String name){
		for(int i=0;i<head.size();i++){
			 if(head.get(i).equals(name)){
				 return getCol(b,i);
			 }
		}
		return null;
	}
	
	public List<Map> toList(){
		List<Map> ls=new ArrayList<Map>();
		init();
		while(isEndOut()!=true){
			ls.add(getCurrRow());
			nextTo();
		}
		return ls;
	}
	
	public void addList( List<Map> ls){
		for(Map m:ls){
			add(m);
		}
	}
	
	public void addCol(byte[] row,int length){
		RowsBuffer rb=results.get(results.size()-1);
		 
		if(rb.addRow(row, rows,row.length)==false){
			RowsBuffer rb2=new RowsBuffer();
			 rb2.addRow(row, rows,row.length);
			 results.add(rb2);
			
		};
		
	}
	
	private Map getColMap(byte[] row){
		Map m=new HashMap();
		for(int i=0;i<head.size();i++){
			m.put(head.get(i), getCol(row,i));
		}
		return m;
	}
	
	
	
	public void fetchEnd(){
		 
	}
	
}
