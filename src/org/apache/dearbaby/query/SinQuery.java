package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.data.SinResultByte;
import org.apache.dearbaby.data.SinResultFac;
import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.impl.sql.compile.ValueNode;
import org.apache.dearbaby.task.QueryTask;
import org.apache.dearbaby.task.TaskPoolManager;

public class SinQuery {

	public String tableName;
	public String alias;
	public ArrayList<String> columns = new ArrayList<String>();
	public SinResult results = SinResultFac.getSinResult();
	private boolean isDrv=false;
	private boolean isHs=false;
	
	//public  results2 = new ArrayList<Map>();
	
	public QueryTaskCtrl taskCtrl=null;
	
	/*默认不是简单查询 */
	public boolean simpleSelect=false;

	public String andCondition = "1=1 ";

	public String sql = "";

	public IExecutor executor;
	
	public QueryTreeNode node;
	
	public ValueNode whereClause;
	
	
	public boolean isOrCond=false;
 
	
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
		if(isHs==true){
			return results.getHsCurrRow();
		}
		return results.getCurrRow();
	}
 
	public Object getCurrCol(String name) {
	//	System.out.println("dddddddddddddff   "+results.getIndex()+"    :  "+name);
		return results.getCurrCol(name);
	}
	public Map nextRow() {
	 
		return results.nextRow();
	}
	
	
	public void nextTo() {
		 
		 results.nextTo();
	}

	public void init() {
		 
		results.init();
	}

	public boolean isEnd() {
		return results.isEnd();
	}

	public boolean isEndOut() {
		return results.isEndOut();
	}
	
	public void hashMatch(JoinType js){
		
	}
	
	public void buildIndex(JoinType jt){
		
		if(jt.left.getTableName().equalsIgnoreCase(alias)){
			System.out.println("nextJoin-buildIndexddd");
			results.buildIndex(jt.left._columnName);
			return;
		}
		if(jt.right.getTableName().equalsIgnoreCase(alias)){
			System.out.println("nextJoin-buildIndexwwww");
			results.buildIndex(jt.right._columnName);
			return;
		}
	}
	
	public boolean nextToJn() {
		return  results.matchNext();
	}
	
	public void initJn() {
		 
		results.init();
	}
	
	public boolean isJnEnd(){
		return true;
	}

	public boolean isJnEndOut() {
		return results.isEndOut();
	}
	
	public boolean match(Object key) {
		return results.firstMatch(key);
	}
	
	public boolean firstMatch( ) {
		return results.firstMatch();
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
	
	public void drv(int begin,int end){
		results.drv(begin, end);
	}
	
	public int  getDrvSize(){
		return results.size();
	}
	
	public Object getHsCurrCol(String name){
		if(isHs==false){
			return getCurrCol(name);
		}
		return results.getHsCurrCol(name);
	}
	
	public Map getHsCurrRow() {
		return results.getHsCurrRow();
	}
	
	public void setDrv(){
		isDrv=true;
		isHs=false;
		results.setIndex(false);
	}
	
	public void indexInit( ){
	 
		 results.indexInit();
	}
	
	public SinQuery clone(){
		SinQuery cloneObj=new SinQuery();
		cloneObj.tableName=this.tableName;
		cloneObj.alias=this.alias;
		cloneObj.columns =this.columns;
		cloneObj.results = this.results.clone();
		
		/*默认不是简单查询 */
		cloneObj.simpleSelect=this.simpleSelect;

		cloneObj.andCondition = this.andCondition;

		cloneObj.sql = this.sql;

		cloneObj.executor=this.executor;
		
		cloneObj.node=this.node;
		
		cloneObj.whereClause=this.whereClause;
		
		
		cloneObj.isOrCond =this.isOrCond ;
		cloneObj.isDrv=this.isDrv;
		cloneObj.isHs=this.isHs;

//		cloneObj.rowId =this.rowId ;

	//	cloneObj.endOut=this.endOut ;
		cloneObj.init();
		
		return cloneObj ;
	}

}
