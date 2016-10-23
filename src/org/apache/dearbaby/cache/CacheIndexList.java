package org.apache.dearbaby.cache;

import java.util.ArrayList;
import java.util.List;

import org.apache.dearbaby.data.IIndex;

public class CacheIndexList {

	public List<CacheIndex> indexs =new ArrayList<CacheIndex>();
	
	public CacheIndex findIndex(String col,int type){
		for(CacheIndex idx:indexs){
			if(idx.col.equalsIgnoreCase(col)&&idx.type==type){
				return idx;
			}
		}
		return null;
	}
	public void addIndex(String col,int type,IIndex dex){
		CacheIndex idx=findIndex(col,type);
		if(idx==null){
			idx = new CacheIndex();
			idx.type=type;
			idx.col=col;
			idx.index=dex;
			indexs.add(idx);
		} 
	}
}
