package org.apache.dearbaby.task;

import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.SinQuery;

public class QueryTask implements Runnable{
	SinQuery sinQuery ;
	
	public QueryTask(SinQuery _sin ){
		sinQuery=_sin;
	
	}
	public void run(){
		try{
			sinQuery.exeSelect();
		}finally{
			if(sinQuery.taskCtrl!=null){
				sinQuery.taskCtrl.finishOne();
			}
			//qm.getTaskCtrl().finishOne();
		}
		
	}
	
}
