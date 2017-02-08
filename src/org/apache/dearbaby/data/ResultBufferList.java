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

public class ResultBufferList extends ResultBuffer{
	protected int rowId = 0;
	protected int bufferId=0;
	protected boolean endOut = false;
	public ArrayList<Map> results = new ArrayList<Map>();
	

	 
	protected int[] dataType=null;
	protected int rowSize=20;
	protected int rows=0;
	
	protected int drvRowId=0;
	protected int endSize=0;
	 
	
	public ResultBufferList(){
		 
		 
		
	}
	 
	
	public int endSize(){
		return endSize;
	}
	
	
	
	public Map getCurrRow0 () {
		
		if (results.size() == 0) {
			return null;
		}
		 
		return results.get(rowId);
		 
	}
	
	public Object getCurrCol0 (String name) {
		
		if (results.size() == 0) {
			return null;
		}
		
		Map m=getCurrRow0();
		
	 
		return m.get(name);
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
	
	 
	public boolean isEndOut() {
		return endOut;
	}
	
	public void add(Map m){
		if(m==null){
			return;
		}
		
		results.add(m);
		
		rows=rows+1;
		endSize++;
		 
	}
	
	public void add(ResultBufferList rb){
		while(rb.endOut==false){
			Map m=rb.getCurrRow();
			add(m);
			rb.nextTo();
		}
	}
	
	
	public void comp(ResultBufferList rb){
		 
		
	}
	
	
 
	
	public int size(){
		//return results2.size();
		return endSize;
	}
	
 
	
	   
	
	public List<Map> toList(){
		 
		return results;
	}
	
	public void addList( List<Map> ls){
		for(Map m:ls){
			add(m);
		}
	}
	
	   
}
