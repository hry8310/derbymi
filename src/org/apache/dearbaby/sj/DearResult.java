package org.apache.dearbaby.sj;

import java.util.List;
import java.util.Map;

public class DearResult {

	private List<ResultMap> res;
	private int rowId = -1;
	
	public DearResult(List<ResultMap> _res){
		res=_res;
	}
	
	public void begin(){
		rowId = -1;
	}
	
	public boolean next(){
		if(rowId>=res.size()-1){
			return false;
		}
		rowId++;
		return true;
	}
}
