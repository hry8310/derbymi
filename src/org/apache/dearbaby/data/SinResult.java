package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.util.ByteUtil;
import org.apache.dearbaby.util.ColCompare;

public interface SinResult {
	 
	
	public void drv(int rowid,int end);
	
	public int endSize();
	
	
	
	public Map getCurrRow () ;
	
	public Object getCurrCol (String name) ;
	
	 
	public Map nextRow() ;
	
	public void nextTo() ;
	
	public void init() ;
	
	public boolean isEnd() ;

	public boolean isEndOut() ;
	
	public void add(Map m);
	
	 
	
	public SinResult clone();
	
	public int size();
	
	public void buildIndex(String col);
}
