package org.apache.dearbaby.sj;

import java.nio.ByteBuffer;

import org.apache.dearbaby.mem.PoolManagerTest;
import org.apache.dearbaby.util.ColCompare;

public class ApiTest {

	public static void main(String[] args){
	    Long l=new Long(10);
	//	System.out.println(l.toString().getBytes().length );
	    runing();
		 
	}
	
	
	public static void runing(){
		PoolManagerTest thr=new PoolManagerTest();
		try{
			System.out.println("dddddddddddddd");
			thr.start();
			Thread.sleep(15000);
			thr.notifying();
		}catch(Exception e){
			
		}
	}
}
