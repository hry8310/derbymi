package org.apache.dearbaby.sj;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.apache.dearbaby.query.JdbcExecutor;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.QueryTaskCtrl;
import org.apache.dearbaby.query.SinQuery;
import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.MathUtil;
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
		 
		 qtt.initDrv(0,8);
		 return  qt.getMatchRows();
	 }
	 
	 public List<ResultMap>  getResult_nu(){
		
		 List<ResultMap> ls =new ArrayList<ResultMap>();
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
			// System.out.println("dddd ");
		 }
		 taskCtrl.await();
		 HashMap h=new  HashMap();
		 for (QueryTreeNode q : qtts) {
			 if(s.groupByList==null){
				 if(s.haveAggr==false){
					 ls.addAll(q.resList);
				}else{
					for(ResultMap r:q.resList){
						System.out.println("fffffffffffrrr  "+r);
						if(ls.isEmpty()){
							ls.add(r);
						}else{
							compRest(ls.get(0),r,s);
						}
					}
				}
				 
			}else{
				if(s.haveAggr==false){
					for(ResultMap r:q.resList){
						putGroup(h,s.groupByList,r);
					}
				}else{
					for(ResultMap r:q.resList){
						putGroupArrg(h,s.groupByList,r,s);
					}
				}
			}
		 }
		 if(s.groupByList!=null){
			 ls.addAll(h.values());
		 }
		 return  ls;
	 }
	 
	 private String genKey(GroupByList gr,ResultMap rm){
		 String k="";
		 for(GroupByColumn col: gr.v){
				ColumnReference colRef=(ColumnReference)col.columnExpression;
				Object o=rm.getObject(colRef.getTableName(), colRef.getColumnName());
				if(o!=null){
					k=k+o.toString()+"<<>>";
				}
			
			};
		 return k;
	 }
	 private void putGroup(HashMap hs,GroupByList gr,ResultMap rm){
		 String key=genKey(gr,rm);
		 if(!hs.containsKey(key)){
			 hs.put(key, rm);
		 }
	 }
	 
	 private void putGroupArrg(HashMap hs,GroupByList gr,ResultMap rm,SelectNode s){
		 String key=genKey(gr,rm);
		 if(!hs.containsKey(key)){
			 hs.put(key, rm);
		 }else{
			 compRest((ResultMap)hs.get(key),rm,s);
		 }
	 }
	 
	 private void compRest(ResultMap rt,ResultMap rf,SelectNode s){
		 for (Object o : s.resultColumns.v) {
				ResultColumn t = (ResultColumn) o;
				if (t._expression instanceof AggregateNode) {
					AggregateNode agg=(AggregateNode)t._expression;
					String fun=agg.aggregateName; 
					ColumnReference c=(ColumnReference)agg.operand;
					String cn=QueryUtil.getAggrColName(t);
					Object vf=rf.getAggrObject(cn);
					Object vt=rt.getAggrObject(cn);
					Object ret=aggre(vt,vf,fun);
					rt.setAggrObject( cn, ret);
				}
			}
	 }
	 
	 private Object aggre(Object vt,Object vf,String fun){
		 Object ret=null;
		 if(fun.equalsIgnoreCase("COUNT")){
			 ret =Integer.valueOf(MathUtil.add(vt.toString(), vf.toString()).toString());
		}
		
		if(fun.equalsIgnoreCase("SUM")){
			System.out.println("dddddddddddddddddd:vt:  "+vt+" , vf  : "+vf);
			ret =Integer.valueOf(MathUtil.add(vt.toString(), vf.toString()).toString());
		}
		if(fun.equalsIgnoreCase("MAX")){
			ret =vt;
			if(ColCompare.compareObject(vt,vf) < 0){
				ret = vf;
			}
		}
		if(fun.equalsIgnoreCase("min")){
			ret =vt;
			if(ColCompare.compareObject(vt,vf) > 0){
				ret = vf;
			}
		}
		return ret;
	 }
	 
}
