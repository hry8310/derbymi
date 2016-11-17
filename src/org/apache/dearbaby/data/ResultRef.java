package org.apache.dearbaby.data;

import java.util.concurrent.atomic.AtomicInteger;

public class ResultRef {
	public AtomicInteger r=new AtomicInteger() ;
	
	public int ref(){
		return r.incrementAndGet();
	}
	public int unRef(){
		return r.decrementAndGet();
	}
}
