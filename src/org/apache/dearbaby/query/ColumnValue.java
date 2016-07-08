package org.apache.dearbaby.query;


public class ColumnValue {

	private String table;
	private String col;
	private Object val;
	
    public void addColVal(String _table,String _col,Object _val){
    	table=_table;
    	col=_col;
    	val=_val;
    			
    }

	public String getTable() {
		return table;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public String getCol() {
		return col;
	}

	public void setCol(String col) {
		this.col = col;
	}

	public Object getVal() {
		return val;
	}

	public void setVal(Object val) {
		this.val = val;
	}
    
     
    
	

}
