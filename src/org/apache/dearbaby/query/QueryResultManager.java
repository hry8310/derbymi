package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.util.JoinTypeUtil;

public class QueryResultManager extends QueryMananger {

	private boolean isEnd = false;
	private boolean first = true;
	public IExecutor executor;
	int i=0;
	
	private ArrayList<SinQuery> joinResult=new  ArrayList<SinQuery>();
	
	private ArrayList<JoinType> js=new  ArrayList<JoinType>();
	public JoinType  jHeader;
	
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
	private boolean isJnMatch=false;
	int matchTms=0;
	public boolean nextJoin() {
		//System.out.println("nextJoin-isJnMatch--0 "+drvQ.isEndOut());
		while(!drvQ.isEndOut()){
			if(isJnMatch==false){
				
				drvQ.nextTo();
				isJnMatch=matchNext(drvQ,js.get(0),joinResult.get(0));
				if(isJnMatch==false){
					continue;
				}
				//System.out.println("nextJoin-isJnMatch--000000   "+joinResult.size());
				for(int i=0;i<joinResult.size()-1;i++){
					System.out.println("nextJoin-isJnMatch-- ");
					isJnMatch=matchNext(joinResult.get(i),js.get(i+1),joinResult.get(i+1));
					if(isJnMatch==false){
						break;
					}
				}
				if(isJnMatch==true){
				//	System.out.println("nextJoin-isJnMatch_tms : "+(matchTms++));
					return true;
				}
			}else{
				for (int i = joinResult.size() - 1; i >= 0; i--) {
					SinQuery sq = joinResult.get(i);
					
					if(sq.nextToJn()==true){
						System.out.println("hhhhhrrrr ");
						return true;
					}else{
						
						if(i==0){
							indexInit();
							isJnMatch=false;
							continue;
						}else{
							sq.firstMatch();
							continue;
						}
						
					}
							
					 
				}
			}
			
		}
		
		return false;
	}
	
	//清除所有索引
	private void indexInit(){
		for (int i = joinResult.size() - 1; i >= 0; i--){
			SinQuery sq = joinResult.get(i);
			sq.indexInit();
		}
	}
	
	private boolean matchNext(SinQuery left,JoinType jt,SinQuery right){
		
		ColumnReference ll;
		ColumnReference rr;
		
		if(jt.left.getTableName().equalsIgnoreCase(left.alias)){
			ll=jt.left;
			rr=jt.right;
		}else{
			ll=jt.right;
			rr=jt.left;
		}
		Object obj=left.getHsCurrCol(ll._columnName);
		return right.match(obj);
		 
	}
	
	public void buildIndex(ArrayList<JoinType > js){
		
		if(drvQ!=null){
			return;
		}
		drvQ = joinResult.get(0);
		
		joinResult.remove(0);
		System.out.println("ddddjoinResult.size()  "+joinResult.size());
		for (int i = 0; i <joinResult.size(); i++) {
			joinResult.get(i).buildIndex(js.get(i));
		}
		drvQ.setDrv();
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
			System.out.println("joinResult-add>>>>>>>>>>> : "+jt);
			joinResult.add(sqi);
			querys.remove(sqi);
			JoinType jtt=jt;
			js.add(jtt);
			jt=jt.next;
			
		}
		return js;
	}
	
	
}
