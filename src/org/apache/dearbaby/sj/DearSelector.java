package org.apache.dearbaby.sj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.AggregateNode;
import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.impl.sql.compile.ResultColumn;
import org.apache.dearbaby.impl.sql.compile.ResultColumnList;
import org.apache.dearbaby.impl.sql.compile.StatementNode;
import org.apache.dearbaby.impl.sql.compile.SubqueryNode;
import org.apache.dearbaby.query.JdbcExecutor;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.ext.DearContext;
import org.apache.derby.iapi.sql.compile.Parser;

public class DearSelector {
	private StatementNode qt;
   
	public void query(String sql) {
		try{
			
			Parser ps =  DearContext.getParser();
			qt = (StatementNode) ps.parseStatement(sql);
	
			QueryMananger qm = new QueryMananger();
			qm.executor=new JdbcExecutor();
			qm.sql=sql;
			qt.genQuery(qm); 
			//qm.readyMutlTask();
			qt.exeQuery();
			//qm.getTaskCtrl().await();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	int i=0;
	
	
	
	 public List<ResultMap>  getResult(){
		return  qt.getMatchRows();
	 }
}
