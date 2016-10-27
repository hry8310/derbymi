package org.apache.dearbaby.sj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.cache.ExcCacheConf;
import org.apache.dearbaby.data.ResultBuffer;
import org.apache.dearbaby.impl.sql.compile.AggregateNode;
import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.impl.sql.compile.CursorNode;
import org.apache.dearbaby.impl.sql.compile.GroupByColumn;
import org.apache.dearbaby.impl.sql.compile.GroupByList;
import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.impl.sql.compile.ResultColumn;
import org.apache.dearbaby.impl.sql.compile.ResultColumnList;
import org.apache.dearbaby.impl.sql.compile.SelectNode;
import org.apache.dearbaby.impl.sql.compile.StatementNode;
import org.apache.dearbaby.impl.sql.compile.SubqueryNode;
import org.apache.dearbaby.query.IExecutor;
import org.apache.dearbaby.query.JdbcExecutor;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.QuerySession;
import org.apache.dearbaby.query.QueryTaskCtrl;
import org.apache.dearbaby.query.SinQuery;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.MathUtil;
import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.ext.DearContext;
import org.apache.derby.iapi.sql.compile.Parser;

public class DearSelector {
	private StatementNode qt;
	
	private IExecutor executor;
   
	public DearSelector(){
		 
	}
	
	
	public void exeQuery(QueryMananger qm){
		qt.genQuery(qm); 
	 	qt.exeQuery(); 
		qt.deciJoin(); 
		
			    //qt.genQuery(qm); 
				//qm.readyMutlTask();
				//qt.exeQuery(); 
				//qt.deciJoin(); 
				//qm.getTaskCtrl().await();
	}
	
	public void query(String sql){
		QuerySession s= QuerySession.jdbcSession();
		 
		query(sql,s);
	}
	
	
	
	public void query(String sql,QuerySession session) {
		try{
			
			Parser ps =  DearContext.getParser();
			qt = (StatementNode) ps.parseStatement(sql);
			
			QueryMananger qm = new QueryMananger();
			qm.cacheConf=session.cacheConf;
			qm.executor= executor;
			qm.sql=sql;
			qm.session=session;
			exeQuery(qm);
		//	qt.genQuery(qm); 
			//qm.readyMutlTask();
			//qt.exeQuery(); 
			//qt.deciJoin(); 
			//qm.getTaskCtrl().await();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	
	
	 



	int i=0;
	
	
	
	 public IExecutor getExecutor() {
		return executor;
	}


	public void setExecutor(IExecutor executor) {
		this.executor = executor;
	}


	public ResultBuffer  getResult(){
		
		 StatementNode qtt=(StatementNode)qt.copy();
		 
		 qtt.initDrv(0,8);
		 return  qt.getMatchRows();
	 }
	 
	 public ResultBuffer getResult_nu(){
		
		 ResultBuffer ls =new ResultBuffer();
		 List<QueryTreeNode>  qtts= qt.copys(5);
		 CursorNode cn=(CursorNode)qt;
		 SelectNode s=(SelectNode)(((CursorNode)qt).resultSet);
		 QueryTaskCtrl taskCtrl=new QueryTaskCtrl();
		 int i=0;
		 for(QueryTreeNode q:qtts){
			 i++;
			 q.taskCtrl=taskCtrl;
		 }
		 taskCtrl.setCount(i);
		 for (QueryTreeNode q : qtts) {
			q.syncGetMatchRows();
			
		 }
		 taskCtrl.await(); 
		 System.out.println("dddd ");
		 for (QueryTreeNode q : qtts) {
		 
			ls.comp(q.resList);
				 
		 }
		 
		 return  ls;
	 }
	  
	 
	 
	  
	 
	 
	 
}
