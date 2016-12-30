package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.Map;

import org.apache.dearbaby.impl.sql.compile.ColumnReference;
import org.apache.dearbaby.util.JoinTypeUtil;

public class QueryResultManager extends QueryMananger {

	private boolean isEnd = false;
	private boolean first = true;
	public IExecutor executor;
 
 
	
	public FetchContext cxt;
	
	public void bindContext(FetchContext _cxt){
		cxt =_cxt;
	}
	
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
	
	
	
	public boolean nextJoin() {
		while(true){
			if(cxt.isQueryNext==false){
				if(next()==false){
					return false;
				}
				cxt.isQueryNext=true;
			}
			boolean r= nextJoin0();
			if(r==true){
				return true;
			}else{
				cxt.isQueryNext=false;
			}
		}
	}
	
	public boolean nextJoin0() {
	//	System.out.println("nextJoin-isJnMatch--0 "+drvQ.results);
		while(!cxt.drvQ.isEndOut()){
			if(cxt.isJnMatch==false){
				
				if (cxt.drvQFirst == true) {
					cxt.drvQFirst = false;
				}else{
					cxt.drvQ.nextTo();
				}
				if(cxt.drvQ.isEndOut()==true){
					break;
				}
				 
				
				cxt.isJnMatch=matchNext(cxt.drvQ,cxt.js.get(0),cxt.joinResult.get(0));
				if(cxt.isJnMatch==false){
					continue;
				}
				//System.out.println("nextJoin-isJnMatch--000000   "+joinResult.size());
				for(int i=0;i<cxt.joinResult.size()-1;i++){
					System.out.println("nextJoin-isJnMatch-- ");
					cxt.isJnMatch=matchNext(cxt.joinResult.get(i),cxt.js.get(i+1),cxt.joinResult.get(i+1));
					if(cxt.isJnMatch==false){
						break;
					}
				}
				if(cxt.isJnMatch==true){
				//	System.out.println("nextJoin-isJnMatch_tms : "+(matchTms++));
					return true;
				}
			}else{
				for (int i = cxt.joinResult.size() - 1; i >= 0; i--) {
					SinQuery sq = cxt.joinResult.get(i);
					
					if(sq.nextToJn()==true){ 
						return true;
					}else{
						
						if(i==0){
							indexInit();
							cxt.isJnMatch=false;
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
	
	public boolean halfDrvNextTo(){
		cxt.drvQ.nextTo();
		return cxt.drvQ.isEndOut();
	}
	
	public boolean halfNextJoinTo() {
		 	if(cxt.isJnMatch==false){
		 		cxt.isJnMatch=matchNext(cxt.drvQ,cxt.js.get(0),cxt.joinResult.get(0));
				if(cxt.isJnMatch==false){
					return false;
				}
				//System.out.println("nextJoin-isJnMatch--000000   "+joinResult.size());
				for(int i=0;i<cxt.joinResult.size()-1;i++){
					System.out.println("nextJoin-isJnMatch-- ");
					cxt.isJnMatch=matchNext(cxt.joinResult.get(i),cxt.js.get(i+1),cxt.joinResult.get(i+1));
					if(cxt.isJnMatch==false){
						break;
					}
				}
				if(cxt.isJnMatch==true){
				//	System.out.println("nextJoin-isJnMatch_tms : "+(matchTms++));
					return true;
				}
			}else{
				for (int i = cxt.joinResult.size() - 1; i >= 0; i--) {
					SinQuery sq = cxt.joinResult.get(i);
					
					if(sq.nextToJn()==true){
						 	return true;
					}else{
						
						if(i==0){
						//	indexInit();
						//	System.out.println("indexInit...... ");
							cxt.isJnMatch=false;
							continue;
						}else{
							sq.firstMatch();
							continue;
						}
						
					}
							
					 
				}
			}
			
	 
		
		return false;
	}
	
	
	//清除所有索引
	private void indexInit(){
		for (int i = cxt.joinResult.size() - 1; i >= 0; i--){
			SinQuery sq = cxt.joinResult.get(i);
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
		
		if(cxt.drvQ!=null){
			return;
		}
		cxt.drvQ = cxt.joinResult.get(0);
		QueryTaskCtrl ctr=new QueryTaskCtrl();
		cxt.joinResult.remove(0);
		System.out.println("ddddjoinResult.size()  "+cxt.joinResult.size());
		ctr.setCount(cxt.joinResult.size());
		for (int i = 0; i <cxt.joinResult.size(); i++) {
			//joinResult.get(i).buildIndex(js.get(i));
			cxt.joinResult.get(i).setTaskCtrlMust(ctr);
			cxt.joinResult.get(i).addBuildIndexTask(js.get(i));
		}
		ctr.await();
		cxt.drvQ.setDrv();
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
		if(cxt.drvQ!=null){
			System.out.println("drvQ     "+cxt.drvQ.tableName);
			cxt.drvQ.drv(begin, end);
			return ;
		}
		if(querys.size()>0)
			querys.get(0).drv(begin, end);
	}
	
	public int  getDrvSize( ){
		if(cxt.drvQ!=null){
			return cxt.drvQ.getDrvSize();
			 
		} 
		return querys.get(0).getDrvSize();
	}
	
	public QueryResultManager copyOf(){
		QueryResultManager newObj=new QueryResultManager();
		copyTo(newObj);
		newObj.cxt=new FetchContext();
		newObj.cxt.js=this.cxt.js;
		newObj.cxt.jHeader=this.cxt.jHeader;
		if(cxt.drvQ!=null)
		{
			newObj.cxt.drvQ=this.cxt.drvQ.clone();
			//newObj.drvQ=this.drvQ;
		}
		newObj.cxt.isJnMatch=this.cxt.isJnMatch;
		//int matchTms=0;
		newObj.cxt.isQueryNext=this.cxt.isQueryNext;
		init();
		return newObj;
	}
	
	public ArrayList<JoinType> analyseJoin(JoinType jt,FetchContext ctx){
		String e=jt.emp;
		SinQuery sq=findQuery(e);
	 
		System.out.println("fistttttttt "+sq.alias);
		ctx.joinResult.add(sq);
		
		querys.remove(sq);
		
		while(jt!=null){
		 
			if(jt.nextTable==null){
				break;
			}
			SinQuery sqi=findQuery(jt.nextTable); 
			ctx.joinResult.add(sqi);
			querys.remove(sqi);
			JoinType jtt=jt;
			ctx.js.add(jtt);
			jt=jt.next;
			
		}
		return ctx.js;
	}
	
	public ArrayList<SinQuery> getJoinSq(){
		return cxt.joinResult;
	}
	
	public SinQuery getDrvQ(){
		return cxt.drvQ;
	}
	
}
