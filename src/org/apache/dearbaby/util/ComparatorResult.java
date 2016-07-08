package org.apache.dearbaby.util;

import java.util.Comparator;

import org.apache.dearbaby.impl.sql.compile.*;
import org.apache.dearbaby.sj.ResultMap;

public class ComparatorResult implements Comparator<ResultMap> {
	OrderByList orderByList ;
	public ComparatorResult(OrderByList orderList){
		this.orderByList=orderList;
	}
	
	@Override
    public int compare(ResultMap u1, ResultMap u2) {
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
