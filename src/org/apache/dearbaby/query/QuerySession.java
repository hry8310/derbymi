package org.apache.dearbaby.query;

import org.apache.dearbaby.cache.ExcCacheConf;
import org.apache.dearbaby.util.DRConstant;

public class QuerySession {

	public ExcCacheConf cacheConf;
	//0-�϶����ã�1-�������ã�2-�϶�ʹ��
	public int disk_use;
	
	public String useDriverTable;
	
	public int resultType=DRConstant.RESULT_LIST;
	
	public static QuerySession jdbcSession(){
		QuerySession s=new QuerySession();
		 
		return s;
	}
	
}
