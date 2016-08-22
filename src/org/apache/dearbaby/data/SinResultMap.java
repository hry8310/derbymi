package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;

public class SinResultMap implements SinResult {
	private int rowId = 0;
	private boolean endOut = false;
//	private ArrayList<Map> results = new ArrayList<Map>();
	public ArrayList<Map> results = new ArrayList<Map>();
	 
	 
	private int[] dataType=null;
	private int rowSize=20;
	private int rows=0;
	
	private int drvRowId=0;
	private int endSize=0;
	
	public HashIndex hashIndex;
	
	private boolean isBuild=false;
	private boolean isIdx=false;
	
	
	public void drv(int rowid,int end){
		drvRowId=rowid;
		rows=end;
		rowId=rowid;
	}
	
	public int endSize(){
		return endSize;
	}
	
	
	
	private Map getCurrRow0 () {
		
		if (results.size() == 0) {
			return null;
		}
		 
		return results.get(rowId);
		 
 
	}
	
	public  Map getCurrRow () {
		if(isBuild==true){
			return getHsCurrRow();
		}
		return getCurrRow0();
		 
	}
	
	private Object getCurrCol0 (String name) {
		if (results.size() == 0) {
			return null;
		}
		Map m=getCurrRow();
	//	return results.get(rowId);
		return m.get(name);
	}
	 
	public  Object getCurrCol (String name) {
		if(isBuild==true){
			return getHsCurrCol(name);
		}
		return getCurrCol0(name);
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
		rows++;
		endSize++;
		results.add(m);
	}
	public void buildIndex(String col){
		System.out.println("buildIndex-col  "+col);  
		if(hashIndex!=null){
			return;
		}
		int ss=rows/10;
		if(ss==0){
			ss=1;
		}
		hashIndex = new HashIndex(ss); 
		for(Map m:results){
			
			hashIndex.addKey(m.get(col), m);
		}
		isBuild=true; 
		//System.exit(0);
	}
	
	public Map getHsCurrRow () {
		return (Map)hashIndex.getCurrRow();
	}
	
	public Object getHsCurrCol (String name) {
		 
		Map m=getHsCurrRow();
	//	System.out.println("alias   name  "+name+"   "+isBuild+" ,  obj : "+m); 
		Object obj= m.get(name);
	//	System.out.println("obj   "+obj+"  name  "+name); 
		return obj;
	}
	
	 
	public SinResultMap clone(){
		SinResultMap ret=new SinResultMap();
		ret.results=this.results; 
		ret.rowId=this.rowId;
		ret.endOut=this.endOut;
		ret.endSize=this.endSize;
		ret.rows=this.rows; 
		return ret;
	}
	
	public int size(){
		//return results2.size();
		return endSize;
	}
	
	public boolean firstMatch(Object key){
		return hashIndex.nextMatch(key);
	 
	}
	
	public void indexInit(){
		hashIndex.init();
	}
	
	public boolean firstMatch(){
		return hashIndex.nextMatch( );
	 
	}
	
	public boolean matchNext(){
		return hashIndex.nextMatch();
	}
	 
	public void setIndex(boolean idx){
		isBuild=idx;
	}
	public boolean getIndex(){
		return isBuild;
	}
}
