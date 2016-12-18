package org.apache.dearbaby.mem;

public class PoolManagerTest extends Thread  {

	public synchronized void waiting(long time){
		try{
			wait(time);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public synchronized void notifying (){
		try{
			notify( );
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void run(){
		while(true){
			try{
				waiting(1000*300);
				System.out.println("dddd");
				 
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
