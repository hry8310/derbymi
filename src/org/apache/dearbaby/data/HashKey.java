package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.Map;

public class HashKey {

	private ArrayList keys=new ArrayList ();
	private ArrayList values=new ArrayList ();
	
	private int rowId=0;
	
	private boolean endOut = false;
	
	public void add(Object key,Object value){ 
		keys.add(key);
		values.add(value);
	}
	
	public void next(){
		rowId++;
		if (rowId > keys.size() - 1) {
			endOut = true;
			rowId = keys.size() - 1;
		}
	}
	
	public boolean nextMatch(Object obj){
		for(int i=rowId;i< keys.size();i++){
			Object o=keys.get(i);
			if(o.equals(obj)){
				rowId=i;
				return true;
			}
		}
		rowId=0;
		return false;
	}
	
	public void init() {
		rowId = 0;
		endOut = false;
	}
	
	public boolean isEnd() {
		return rowId>=keys.size() -1;
	}

	public boolean isEndOut() {
		return endOut;
	}
	
	 
	
}
