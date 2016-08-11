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
		 
		return results.get(rowId);
		 
 
	}
	
	public Object getCurrCol (String name) {
		if (results.size() == 0) {
			return null;
		}
		Map m=getCurrRow();
	//	return results.get(rowId);
		return m.get(name);
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
		 
		rows++;
		endSize++;
		results.add(m);
	}
	public void buildIndex(String col){
		if(hashIndex!=null){
			return;
		}
		hashIndex = new HashIndex(rows/10); 
		for(Map m:results){
			hashIndex.addKey(m.get(col), m);
		}
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
	
	 
}
