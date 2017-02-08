package org.apache.dearbaby.data;

import java.util.ArrayList;

public class HashIndex implements IIndex {

	int hashSize;
	
	HashKey hash[];
	
	int hashId=-1;
	
	Object matchKey=null;
	SinResult result;
	String colName;
	
	public HashIndex(int size,SinResult sin,String name){
		hashSize=size;
		result=sin;
		colName =name;
		hash=new HashKey[size];
	}
	public void init(){
		matchKey=null;
		hashId=-1;
		
	}
	
	public void indexInit(){
		 
		hash[hashId].init();
		
	}
	
	public void addKey(Object key,Object value){
		
		int hsid =(int)hash(key);
		if(hash[hsid]==null){
			hash[hsid]=new HashKey(result,colName);
		}
		hash[hsid].add(key, value);
	}
	
	public void matchInit(Object key){
		hashId = (int)hash(key);
		hash[hashId].init();
	}
	

	public boolean nextMatch(Object key){
		hashId = (int)hash(key);
		if(hashId==-1){
			return false;
		}
		HashKey hk=hash[hashId];
		matchKey=key;
		if(hk==null){
			return false;
		}
		boolean k= hk.nextMatch(key);
		return k;
		
	}
	
	public boolean nextMatch(){
		HashKey hk=hash[hashId];
	
		return  hk.nextMatch(matchKey);
	}
	
	public boolean firstMatch(){
		HashKey hk=hash[hashId];
		 
		return  hk.nextMatch(matchKey);
	}
	
	public Object getCurrRow(){
	
		if(hashId==-1){
			return null;
		}
		
		return hash[hashId].getCurrRow();
	}
	
	private long hash(Object key){
		if(key==null){
			return -1;
		}
		long code = key.hashCode();
		if(code<0)
			code=code*(-1);
			
		return code%hashSize;
	}
	public HashIndex clone(){
		HashIndex hi=new HashIndex(this.hashSize,result,colName);
		hi.hashSize=this.hashSize;
		
		for(int i=0;i< hashSize;i++){
			if(this.hash[i]!=null)
			hi.hash[i]=this.hash[i].clone();
		}
		
		hi.hashId=-this.hashId;
		
		hi.matchKey=this.matchKey;
		return hi;
	}
	
	//no-use
	public void buildEnd(){
		
	}
}

