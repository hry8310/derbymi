package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.Map;

import org.apache.dearbaby.util.JoinTypeUtil;

public class QueryResultManager extends QueryMananger {

	private boolean isEnd = false;
	private boolean first = true;
	public IExecutor executor;
	int i=0;
	
	public ArrayList<SinQuery> joinResult=new  ArrayList<SinQuery>();
	
	public ArrayList<JoinType> js=new  ArrayList<JoinType>();
	
	public boolean next() {
		
		if (first == true) {
			first = false;
			return true;
		}

		if (isEnd) {
			return false;
		}
	//	System.out.println("next  : "+(i++));
		for (int i = querys.size() - 1; i >= 0; i--) {
			SinQuery sq = querys.get(i);
			if (sq.isEnd()) {
				if (i == 0) {
					isEnd = true;
					return false;
				}
				sq.init();
			} else {
			//	sq.nextRow();
				sq.nextTo();
				return true;
			}
		}
		return false;
	}
	
	
	private SinQuery drvQ=null;
	public boolean nextJoin(ArrayList<JoinType > js) {
		
		for (int i = joinResult.size() - 1; i >= 0; i--) {
			SinQuery sq = joinResult.get(i);
			if (sq.isJnEnd()) {
				if (i == 0) {
					isEnd = true;
					return false;
				}
				sq.initJn();
			} else {
				
				sq.nextTo();
				return true;
			}
			//JoinType jt=JoinTypeUtil.findJoin(js,qs.querys.get(i),qs.querys.get(j));
		}
		return false;
	}
	
	public void buildIndex(ArrayList<JoinType > js){
		if(drvQ!=null){
			return;
		}
		drvQ = joinResult.get(0);
		joinResult.remove(0);
		for (int i = 0; i <joinResult.size(); i++) {
			System.out.println("building");
			joinResult.get(i).buildIndex(js.get(i));
		}
	}
	
	
	public void init() {
		for (int i = querys.size() - 1; i >= 0; i--) {
			SinQuery sq = querys.get(i);
			sq.init();
		}
		first = true;
		isEnd=false;
	}

	public Map getCurrRow(String alias) {
		for (int i = querys.size(); i < 0; i--) {
			SinQuery q = querys.get(i);
			if (q.alias.equals(alias)) {
				return q.getCurrRow();
			}
		}
		return null;
	}

	public boolean isEnd() {
		return isEnd;
	}

	public int size() {
		return querys.size();
	}

	public ArrayList<SinQuery> getQrys() {
		return querys;
	}

	public void add(SinQuery q){
		boolean found=false;
		for(SinQuery sq:querys){
			if(sq.alias.equalsIgnoreCase(q.alias)){
				found=true;
			}
		}
		if(found==false){
			querys.add(q);
		}
	}
	
	public void initDrv(int begin,int end){
		//System.out.println(querys);
		querys.get(0).drv(begin, end);
	}
	
	public int  getDrvSize( ){
		 
		return querys.get(0).getDrvSize();
	}
	
	public QueryResultManager copyOf(){
		QueryResultManager newObj=new QueryResultManager();
		copyTo(newObj);
		init();
		return newObj;
	}
	
	public ArrayList<JoinType> ans(JoinType jt){
		String e=jt.emp;
		SinQuery sq=findQuery(e);
	 
		
		joinResult.add(sq);
		
		querys.remove(sq);
		
		while(jt!=null){
		 
			if(jt.nextTable==null){
				break;
			}
			SinQuery sqi=findQuery(jt.nextTable);
			joinResult.add(sqi);
			querys.remove(sqi);
			JoinType jtt=jt;
			js.add(jtt);
			jt=jt.next;
			
		}
		return js;
	}
	
	
}
