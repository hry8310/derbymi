package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.Map;

public class QueryResultManager extends QueryMananger {

	private boolean isEnd = false;
	private boolean first = true;
	public IExecutor executor;
	
	public boolean next() {
		if (first == true) {
			first = false;
			return true;
		}

		if (isEnd) {
			return false;
		}
		for (int i = querys.size() - 1; i >= 0; i--) {
			SinQuery sq = querys.get(i);
			if (sq.isEnd()) {
				if (i == 0) {
					isEnd = true;
					return false;
				}
				sq.init();
			} else {
				sq.nextRow();
				return true;
			}
		}
		return false;
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
	
	
	public QueryResultManager getNew(){
		QueryResultManager newObj=new QueryResultManager();
		for(SinQuery s:querys){
			newObj.querys.add(s.clone());
		}
		
		return newObj;
	}
}
