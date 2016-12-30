package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.Map;

import org.apache.dearbaby.util.ColCompare;

public class HashKey {

	private ArrayList<Long> values=new ArrayList<Long> ();
	
	private int rowId=0;
	
	private int preId=0;
	
	private boolean endOut = false;
	private SinResult res;
	private String colName ;
	private int headId;
	public HashKey(SinResult s,String name){
		res=s;
		headId=res.getHeadId(name);
		colName=name;
	}
	public void add(Object key,Object value){ 
	 
		values.add((long)value);
	}
	
	public void next(){
		rowId++;
		if (rowId > values.size() - 1) {
			endOut = true;
			rowId = values.size() - 1;
		}
	}
	
	private Object getKey(int i){
		long l=values.get(i);
		return res.getColVal(l, headId);
	 
	}
	public boolean nextMatch(Object obj){
	//	System.out.println("nextMatch   "+keys.size());
		for(int i=preId;i< values.size();i++){
			Object o=getKey(i);
	//		System.out.println("unhhhhrr   "+obj +"    ,  "+o);
			if(ColCompare.compareObject(obj, o)==0){
				rowId=i;
				preId=i+1;
			 
			//	System.out.println("hhhhrr   "+obj +"    ,  "+o+"  rowId  "+rowId);
				 
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
		return rowId>=values.size() -1;
	}

	public boolean isEndOut() {
		return endOut;
	}
	
	public Object getCurrRow(){
		
		Object o= values.get(rowId);
		return o;
	}
	 
	public HashKey clone(){
		HashKey hk=new HashKey(res,colName);
 
		hk.values=this.values;
		
		hk.rowId=this.rowId;
		
		hk.preId=this.preId;
		
		hk.endOut = this.endOut ;
		return hk;
		
	}
}
