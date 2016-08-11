package org.apache.dearbaby.task;

import java.util.List;

import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;
import org.apache.dearbaby.query.QueryMananger;
import org.apache.dearbaby.query.SinQuery;
import org.apache.dearbaby.sj.ResultMap;

public class JoinTask implements Runnable{
	QueryTreeNode qt ;
	
	public JoinTask(QueryTreeNode _qt ){
		qt=_qt;
	
	}
	public void run(){
		try{
			qt.resList=qt.getMatchRows();
		}finally{
			System.out.println("qt  "+qt.resList.size());;
			if(qt.taskCtrl!=null){
				qt.taskCtrl.finishOne();
			}
			//qm.getTaskCtrl().finishOne();
		}
		
	}
	
}
