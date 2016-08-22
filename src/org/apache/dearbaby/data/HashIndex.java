package org.apache.dearbaby.data;

import java.util.ArrayList;

public class HashIndex {

	int hashSize;
	
	HashKey hash[];
	
	int hashId=-1;
	
	Object matchKey=null;
	
	public HashIndex(int size){
		hashSize=size;
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
			hash[hsid]=new HashKey();
		}
		hash[hsid].add(key, value);
	}
	
	public void matchInit(Object key){
		hashId = (int)hash(key);
		hash[hashId].init();
	}
	

	public boolean nextMatch(Object key){
		hashId = (int)hash(key);
		HashKey hk=hash[hashId];
		matchKey=key;
	
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
			System.out.println("hashIdhashIdhashIdhashId  "+hashId);
			return null;
		}
		
		return hash[hashId].getCurrRow();
	}
	
	private long hash(Object key){
		long code = key.hashCode();
		if(code<0)
			code=code*(-1);
			
		return code%hashSize;
	}
	
	
}

