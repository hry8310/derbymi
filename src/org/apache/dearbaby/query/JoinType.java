package org.apache.dearbaby.query;

import org.apache.dearbaby.impl.sql.compile.ColumnReference;

public class JoinType {
	public static final int HASH=1;
	public static final int IDX=2;
	public static final int UN=3;
	public ColumnReference left;
	public ColumnReference right;
	public String operator;
	public int type;
	
	public JoinType prev;
	public JoinType next;
	
	public String emp;
	public String nextTable;
	
	
	public JoinType(ColumnReference  le,ColumnReference  rt, String op){
		if(op.equals("=")){
			type=HASH;
		}else if(op.equals("<")||op.equals(">")) {
			type=IDX;
		}else{
			type=UN;
		}
		operator=op;
		left=le;
		right=rt;
	}
}
