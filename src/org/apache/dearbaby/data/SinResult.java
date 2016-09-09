package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.dearbaby.query.JoinType;
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
	
	public void buildIndex(String col,JoinType jt,int ct);
	
	public Object getHsCurrCol(String name);
	public Map getHsCurrRow( );
	public boolean firstMatch(Object key);
	
	public boolean firstMatch();
	
	public boolean matchNext();
	public void indexInit();
	
	public void setIndex(boolean idx);
	
	public boolean getIndex();
}
