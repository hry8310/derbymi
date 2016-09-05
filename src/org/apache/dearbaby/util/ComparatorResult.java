package org.apache.dearbaby.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.*;
import org.apache.dearbaby.sj.ResultMap;

public class ComparatorResult implements Comparator<Map> {
	OrderByList orderByList ;
	public ComparatorResult(OrderByList orderList){
		this.orderByList=orderList;
	}
	
	@Override
    public int compare(Map u11, Map u22) {
	 
		ResultMap u1=new ResultMap((HashMap)u11);
		ResultMap u2=new ResultMap((HashMap)u22);
		for(OrderByColumn oc:orderByList.v ){
				ColumnReference  c	=(ColumnReference)oc.expression;
				String col=c._columnName;
				String alias = c._qualifiedTableName.tableName;
				Object o1= u1.getObject(alias, col);
				Object o2= u2.getObject(alias, col);
				int r = ColCompare.compareObject(o1, o2);
				if(r==0){
					continue;
				}
				return r;
				
		}
		return 0;
    }

}
