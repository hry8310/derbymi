package org.apache.dearbaby.query;

import java.util.ArrayList;

import org.apache.dearbaby.util.ColCompare;


public class RowColumn {

	private ArrayList<ColumnValue> row = new ArrayList<ColumnValue>();
    
	public void add(ColumnValue cv){
		row.add(cv);
	}
	public ArrayList<ColumnValue> getRow(){
		return row;
	}
	
	public void add2Row(String table,String col,Object obj){
		ColumnValue colVal=new ColumnValue();
		colVal.addColVal(table, col, obj);
		row.add(colVal);
	}
	
	public void replaceRow(String table,String col,Object obj){
		ColumnValue c=findRow(table,col);
		if(c!=null){
			c.setVal(obj);
		}
	}
	
	public ColumnValue findRow(String table,String col){
		for(ColumnValue c:row){
			if(c.getTable().equalsIgnoreCase(table)&&c.getCol().equalsIgnoreCase(col)){
				return c ;
			}
		}
		return null;
	}
	
	public Object findVal(String table,String col){
		for(ColumnValue c:row){
			if(c.getTable().equalsIgnoreCase(table)&&c.getCol().equalsIgnoreCase(col)){
				return c.getVal();
			}
		}
		return null;
	}
	
	public boolean include(RowColumn rc){
		for(ColumnValue cv:rc.getRow()){
			boolean foundCol=false;
			for(ColumnValue cv2:row){
				if(cv.getTable().equalsIgnoreCase(cv2.getTable())){
					foundCol=true;
					if(ColCompare.compareObject(cv.getVal(), cv2.getVal()) != 0){
						return false;
					}
				}
			}
			return foundCol;
		}
		return true;
	}
	
	public boolean isEmpty(){
		return row.isEmpty();
	}

}
