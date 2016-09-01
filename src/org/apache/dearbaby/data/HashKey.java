package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.Map;

import org.apache.dearbaby.util.ColCompare;

public class HashKey {

	private ArrayList keys=new ArrayList ();
	private ArrayList values=new ArrayList ();
	
	private int rowId=0;
	
	private int preId=0;
	
	private boolean endOut = false;
	
	public void add(Object key,Object value){ 
		//System.out.println("hashkey-add   "+keys.size());
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
	//	System.out.println("nextMatch   "+keys.size());
		for(int i=preId;i< keys.size();i++){
			Object o=keys.get(i);
			//System.out.println("hhhhrr   "+obj +"    ,  "+o);
			if(ColCompare.compareObject(obj, o)==0){
				rowId=i;
				preId=i+1;
				//System.out.println("hhhhrr   ");
				return true;
			}
		}
		rowId=0;
		preId=0;
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
	
	public Object getCurrRow(){
		return values.get(rowId);
	}
	 
	public HashKey clone(){
		HashKey hk=new HashKey();

		hk.keys=this.keys;
		hk.values=this.values;
		
		hk.rowId=this.rowId;
		
		hk.preId=this.preId;
		
		hk.endOut = this.endOut ;
		return hk;
		
	}
}
