package org.apache.dearbaby.task;

import org.apache.dearbaby.query.JoinType;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.SinQuery;

public class BuildTask implements Runnable{
	SinQuery sinQuery ;
	JoinType jt;
	
	public BuildTask(SinQuery _sin,JoinType _jt ){
		sinQuery=_sin;
		jt=_jt;
	
	}
	public void run(){
		try{
			System.out.println("dddddddddddddddd");;
			sinQuery.buildIndex(jt);
 		}catch(Exception e){
 			e.printStackTrace();
 		}finally{
			if(sinQuery.taskCtrl!=null){
				sinQuery.taskCtrl.finishOne();
			}
			//qm.getTaskCtrl().finishOne();
		}
		
	}
	
}
