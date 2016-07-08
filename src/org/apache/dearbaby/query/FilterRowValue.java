package org.apache.dearbaby.query;

import java.util.ArrayList;

public class FilterRowValue {

	 
 
	private ArrayList< RowColumn> resultset = new ArrayList<RowColumn>();
	public RowColumn row = new RowColumn();
	private int rowId = -1;
	
	
	public void flushRow(){
		if(!row.isEmpty()){
			resultset.add(row);
			row = new RowColumn();
		}
	}
	
	public void add2Row(String table,String col,Object obj){
		ColumnValue colVal=new ColumnValue();
		colVal.addColVal(table, col, obj);
		row.add(colVal);
	}
	
	public void add2Row(RowColumn  colVal){
		for(ColumnValue cv:colVal.getRow()) {
		   row.add(cv);
		}
	}
	
	public void flushTheRow(RowColumn  rc){
		if(!rc.isEmpty()){
			resultset.add(rc);
			 
		}
	}

	public boolean next(){
		if(rowId>=resultset.size()-1){
			return false;
		}
		rowId++;
		return true;
	}
	
	public void again(){
		rowId=-1;
	}
	public  RowColumn getCurrRow(){
		return resultset.get(rowId);
	}
	
	public Object getCurrVal(String table,String col ){
		 RowColumn row=getCurrRow();
		 for(ColumnValue cv: row.getRow()){
			 if(cv.getTable().equalsIgnoreCase(table)&&cv.getCol().equalsIgnoreCase(col)){
				 return cv.getVal();
			 }
		 }
		 return null;
	}
	
	public RowColumn findRow(RowColumn c){
		for(RowColumn rc:resultset){
			if(rc.include(c)){
				return rc;
			}
		}
		return null;
	}
	
	public RowColumn findOrCreateRow (RowColumn c){
		RowColumn f=findRow(c);
		if(f==null){
			f=c; 
			
		}
		row=f;
		return f;
	}
	 
}
