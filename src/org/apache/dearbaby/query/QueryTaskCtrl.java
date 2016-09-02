package org.apache.dearbaby.query;

import java.util.concurrent.CountDownLatch;

public class QueryTaskCtrl {

	 
    private CountDownLatch latch; 
    public void setCount(int ts){
    	latch=new CountDownLatch(ts);
    }
    
    public void finishOne(){
    	
    	latch.countDown();
      }
    
    public void await( ){
    	try{
     		latch.await( );
     	}catch(Exception e){
    		e.printStackTrace();
    	}
    }
}
