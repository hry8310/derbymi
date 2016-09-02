package org.apache.dearbaby.sj;

import java.util.HashMap;

public class ResultMap {
	HashMap m;
	
	public ResultMap(HashMap _m){
		m=_m;
	}
	
	public Object getObject(String alias,String col){
		String key=alias.toUpperCase()+"."+col.toUpperCase();
		return m.get(key);
	}
	
	public Object getAggrObject( String col){
		String key="#."+col.toUpperCase();
		return m.get(key);
	}
	
	public  void setAggrObject( String col,Object obj){
		String key="#."+col.toUpperCase();
		  m.put(key,obj);
	}
	
    @Override  
   public boolean equals(Object obj) {  
        if(obj == null) return false;  
        if(this == obj) return true;  
        if(obj instanceof ResultMap){   
        	ResultMap rest =(ResultMap)obj;  
        	HashMap mm=rest.m;
           boolean mb= mm.equals(m);
           return mb;
        }  
        return false;  
    } 
    
   
    @Override  
   public int hashCode() {  
    	
           return m.hashCode();  
    }  



}
