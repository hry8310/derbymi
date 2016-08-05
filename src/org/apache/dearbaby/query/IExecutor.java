package org.apache.dearbaby.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.dearbaby.data.SinResult;

public interface IExecutor {

	public SinResult exe(String sql,List<String> columns);
}
