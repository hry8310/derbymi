package org.apache.dearbaby.sj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.AggregateNode;
import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.impl.sql.compile.ResultColumn;
import org.apache.dearbaby.impl.sql.compile.ResultColumnList;
import org.apache.dearbaby.impl.sql.compile.StatementNode;
import org.apache.dearbaby.impl.sql.compile.SubqueryNode;
import org.apache.dearbaby.query.JdbcExecutor;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.QueryTaskCtrl;
import org.apache.dearbaby.query.SinQuery;
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
			System.out.println("dddddddddddddddddddddd--001");
			qt.deciJoin();
			System.out.println("dddddddddddddddddddddd--002");
			//qm.getTaskCtrl().await();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	int i=0;
	
	
	
	 public List<ResultMap>  getResult(){
		
		 StatementNode qtt=(StatementNode)qt.copy();
		 
		 qtt.initDrv(0,20);
		 return  qt.getMatchRows();
	 }
	 
	 public List<ResultMap>  getResult_nu(){
		
		 List<ResultMap> ls =new ArrayList<ResultMap>();
		 List<QueryTreeNode>  qtts= qt.copys(5);
		 QueryTaskCtrl taskCtrl=new QueryTaskCtrl();
		 int i=0;
		 for(QueryTreeNode q:qtts){
			 i++;
			 q.taskCtrl=taskCtrl;
		 }
		 taskCtrl.setCount(i);
		 for (QueryTreeNode q : qtts) {
			q.syncGetMatchRows();
			// System.out.println("dddd "+lss.size());
		 }
		 taskCtrl.await();
		 
		 for (QueryTreeNode q : qtts) {
				ls.addAll(q.resList);
		 }
		 return  ls;
	 }
}
