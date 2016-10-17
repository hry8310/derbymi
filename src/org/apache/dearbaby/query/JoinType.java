package org.apache.dearbaby.query;

import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.util.DRConstant;

public class JoinType {
	public static final int HASH=1;
	public static final int SORT=2;
	public static final int UN=3;
 

	
	public ColumnReference left;
	public ColumnReference right;
	public String operator;
	public int type;
	public int opr;
	
	public JoinType prev;
	public JoinType next;
	
	public String emp;
	public String nextTable;
	public QueryMananger qm;
	
	public JoinType(ColumnReference  le,ColumnReference  rt, String op ,  QueryMananger _qm){
		if(op.equals("=")){
			type=HASH;
		}else if(op.equals("<")||op.equals(">")||op.equals("<=")||op.equals(">=")) {
			type=SORT;
		}else{
			type=UN;
		}
		setOprVal(op);
		qm=_qm;
		operator=op;
		left=le;
		right=rt;
	}
	
	private void setOprVal(String op){
		if(op.equals("<")){
			opr=DRConstant.LESS;
		}else if(op.equals("<=")){
			opr=DRConstant.LESSEQ;
		}else if(op.equals(">")){
			opr=DRConstant.LAG;
		}else if(op.equals(">=")){
			opr=DRConstant.LAGEQ;
		}
	}
	
	public void setToLeft(String key){
		if(right.getTableName().equalsIgnoreCase(key)){
			chgx();
		}
	}
	private void chgx(){
		if(opr==DRConstant.LESS){
			opr=DRConstant.LAG;
			operator=">";
		}else if(opr==DRConstant.LESSEQ){
			opr=DRConstant.LAGEQ;
			operator=">=";
		}else if(opr==DRConstant.LAG){
			opr=DRConstant.LESS;
			operator="<";
		}else if(opr==DRConstant.LAGEQ){
			opr=DRConstant.LESSEQ;
			operator="<=";
		}
		System.exit(0);
		ColumnReference l=left;
		left=right;
		right=l;
	}
}
