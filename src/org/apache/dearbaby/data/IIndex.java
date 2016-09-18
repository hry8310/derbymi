package org.apache.dearbaby.data;

import java.util.ArrayList;

public interface IIndex {

	 
	 
	public void init() ;
	
	public void indexInit();
	
	public void addKey(Object key,Object value);
	
	public void matchInit(Object key);

	public boolean nextMatch(Object key);
	
	public boolean nextMatch();
	
	public boolean firstMatch();
	
	public Object getCurrRow();
	
	public void buildEnd();
	 
	public IIndex clone();
	
}

