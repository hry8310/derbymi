package org.apache.dearbaby.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.dearbaby.util.ColCompare;
import org.apache.dearbaby.util.CompareUtil;
import org.apache.dearbaby.util.DRConstant;
 

public class SortIndex implements IIndex {

	int size;
	
	SortEle sort[];
	
	int sortId=-1;
	
	int idx=0;
	
	int type;
	
	Object matchKey=null;
	
	public SortIndex(int _size,int _type){
		size=_size;
		type=_type;
		sort=new SortEle[size];
	}
	public void init(){
		matchKey=null;
		sortId=-1;
		
	}
	
	public void indexInit(){
		 
		init();
		
	}
	
	public void addKey(Object key,Object value){
		SortEle s=new SortEle();
	
		s.bufRow=(long)value;
		s.key=key;
		sort[idx++]=s;
	}
	
	public void matchInit(Object key){
		 
	}
	

	public boolean nextMatch(Object key){
		
		matchKey=key;
		for(int i=sortId+1;i<size;i++){
			int r=ColCompare.compareObject(key, sort[i].key);
			if(type==DRConstant.LESS){
				if(r<0){
					sortId=i;
					return true;
				}
			}else if(type==DRConstant.LESSEQ){
				if(r<=0){
					 
					sortId=i;
					return true;
				}
			}else if(type==DRConstant.LAG){
				if(r>0){
				 
					sortId=i;
					return true;
				}
			}else if(type==DRConstant.LAGEQ){
				if(r>=0){
					sortId=i ;
					return true;
				}
			}
			
			break;
		}
		return false;
		
	}
	
	public boolean nextMatch(){
		for(int i=sortId+1;i<size;i++){
			int r=ColCompare.compareObject(matchKey, sort[i].key);
			
			if(type==DRConstant.LESS){
				 
				if(r<0){
					sortId=i;
					return true;
				}
			}else if(type==DRConstant.LESSEQ){
				if(r<=0){ 
					sortId=i; 
					return true;
				}
			}else if(type==DRConstant.LAG){
				if(r>0){
					sortId=i;
					return true;
				}
			}else if(type==DRConstant.LAGEQ){
				if(r>=0){
					sortId=i;
					return true;
				}
			}
			 
			break;
		}
		sortId=-1;
		return false;
	}
	
	public boolean firstMatch(){
		return nextMatch();
	}
	
	public Object getCurrRow(){

		
		if(sortId==-1){
			return null;
		}
	 
		return sort[sortId].bufRow;
	}
	
	public void buildEnd(){
		Comparator cp=new Comparator<SortEle>(){
			public int compare(SortEle sa1, SortEle sa2) {
				int r=ColCompare.compareObject(sa1.key, sa2.key);
				//System.out.println("dddddddddd  2:  "+sa1.key  +  "  2 : "+sa2.key+"  r:   "+r);
				if(r==0){
					return 0;
				}
				if(r<0){
					if(type==DRConstant.LESS||type==DRConstant.LESSEQ){
						return 1;
					}else{
						return -1;
					}
				}
				if(r>0){
					if(type==DRConstant.LESS||type==DRConstant.LESSEQ){
						return -1;
					}else{
						return 1;
					}
				}
				return 0;
			}
		};
		System.out.println("-----dddddddddddddddvvvvvvvv  build-end");
		Arrays.sort(sort, cp);
		System.out.println("-----dddddddddddddddvvvvvvvv  build-end-2");
		int r=ColCompare.compareObject(sort[0].key, sort[1].key);
		
	}
	
	public SortIndex clone(){
		SortIndex hi=new SortIndex(this.size,this.type);
		hi.idx=idx;
		hi.sortId=-1;
		hi.sort=sort;
		hi.size=size;
		hi.type=type;
		hi.sortId=sortId;
		return hi;
	}
	
	private Object getVal(){
		return null;
	}
	
}

