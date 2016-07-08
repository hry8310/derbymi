package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IExecutor {

	public ArrayList<Map> exe(String sql,List<String> columns);
}
