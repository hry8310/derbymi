package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.data.SinResult;
import org.apache.dearbaby.impl.sql.compile.QueryTreeNode;

public interface IExecutor {

	public SinResult exe(QueryMananger qm,String table,String sql,List<String> columns);
}
