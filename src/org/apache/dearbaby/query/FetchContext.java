package org.apache.dearbaby.query;

import java.util.ArrayList;

public class FetchContext {

	public ArrayList<SinQuery> joinResult=new  ArrayList<SinQuery>();
	
	public ArrayList<JoinType> js=new  ArrayList<JoinType>();
	public JoinType  jHeader;
	public SinQuery drvQ=null;
	public boolean isJnMatch=false;
	//int matchTms=0;
	public boolean isQueryNext=false;
	public boolean drvQFirst=true;	 
}
