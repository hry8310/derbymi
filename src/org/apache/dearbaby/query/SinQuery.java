package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.impl.sql.compile.ValueNode;
import org.apache.dearbaby.task.QueryTask;
import org.apache.dearbaby.task.TaskPoolManager;

public class SinQuery {

	public String tableName;
	public String alias;
	public ArrayList<String> columns = new ArrayList<String>();
	public ArrayList<Map> results = new ArrayList<Map>();
	
	public QueryTaskCtrl taskCtrl=null;
	
	/*默认不是简单查询 */
	public boolean simpleSelect=false;

	public String andCondition = "1=1 ";

	public String sql = "";

	public IExecutor executor;
	
	public QueryTreeNode node;
	
	public ValueNode whereClause;
	
	
	public boolean isOrCond=false;

	private int rowId = 0;

	private boolean endOut = false;
	
	public boolean setTaskCtrl(QueryTaskCtrl taskCtrl){
		if(this.taskCtrl!=null){
			return false;
		}
		this.taskCtrl=taskCtrl;
		return true;
	}

	public void genSql(QueryMananger q) {
		String s = "select ";
		for (int i = 0; i < columns.size(); i++) {
			s = s +alias+"."+ columns.get(i) + " ";
			if (i < columns.size() - 1) {
				s = s + ", ";
			} else {
				s = s + " ";
			}
		}
		s = s + " from " + tableName+" "+alias +" ";
		if(simpleSelect==true){
			if(whereClause!=null){
				s=s+ " where "+q.sql.substring( whereClause.getBeginOffset(),whereClause.getEndOffset()+1);
			}
		}else{
			s=s+ " where " + andCondition;
		}
		sql = s;
		addExeTask( );
		//exeSelect();
		System.out.println("sql---:  " + sql);
	}

	public Map getCurrRow() {
		
		if (results.size() == 0) {
			return null;
		}

		return results.get(rowId);
	}

	public Object getCurrCol(String name) {
		Map map= getCurrRow();
		return map.get(name);
	}

	public Map nextRow() {
		rowId++;

		if (rowId > results.size() - 1) {
			endOut = true;
			rowId = results.size() - 1;
		}
		return getCurrRow();
	}

	public void init() {
		rowId = 0;
		endOut = false;
	}

	public boolean isEnd() {
		return rowId >= results.size() - 1;
	}

	public boolean isEndOut() {
		return endOut;
	}

	public void addExeTask(){
		QueryTask qtask=new QueryTask(this);
		TaskPoolManager.putTask(qtask);
	}
	
	public void exeSelect() {
		try {
			if (sql == null || sql.length() == 0) {
				return;
			}
			results=executor.exe(sql, columns);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void exeGroupBy(QueryTreeNode qn){
		
	}
	
	public SinQuery clone(){
		SinQuery cloneObj=new SinQuery();
		cloneObj.tableName=this.tableName;
		cloneObj.alias=this.alias;
		cloneObj.columns =this.columns;
		cloneObj.results = this.results;
		
		/*默认不是简单查询 */
		cloneObj.simpleSelect=this.simpleSelect;

		cloneObj.andCondition = this.andCondition;

		cloneObj.sql = this.sql;

		cloneObj.executor=this.executor;
		
		cloneObj.node=this.node;
		
		cloneObj.whereClause=this.whereClause;
		
		
		cloneObj.isOrCond =this.isOrCond ;

		cloneObj.rowId =this.rowId ;

		cloneObj.endOut=this.endOut ;
		cloneObj.init();
		
		return cloneObj ;
	}

}
