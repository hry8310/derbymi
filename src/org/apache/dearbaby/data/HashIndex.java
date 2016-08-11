package org.apache.dearbaby.data;

import java.util.ArrayList;

public class HashIndex {

	int hashSize;
	
	HashKey hash[];
	
	int hashId=-1;
	
	public HashIndex(int size){
		hashSize=size;
		hash=new HashKey[size];
	}
	
	public void addKey(Object key,Object value){
		int hsid = key.hashCode()%hashSize;
		if(hash[hsid]==null){
			hash[hsid]=new HashKey();
		}
	}
	
	public void match(Object key){
		hashId = key.hashCode()%hashSize;
		hash[hashId].init();
	}
	
	
}

