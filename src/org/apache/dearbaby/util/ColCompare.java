package org.apache.dearbaby.util;

public class ColCompare {

	public static final int compareObject(Object l, Object r) {
		int ret = 0;
		String clsr = r.getClass().getName();
		String clsl = l.getClass().getName();
		;
		if (isNum(clsr) == 1 && isNum(clsl) == 1) {
			ret = ByteUtil.compareNumberByte(l.toString().getBytes(), r
					.toString().getBytes());
		} else if (isNum(clsr) != 1 && isNum(clsl) != 1) {
			ret = CompareUtil.compareString(l.toString(), r.toString());
		}
		return ret;
	}

	public static final int compareValue(Object l, String r) {
		int ret = 0;

		if (r.trim().startsWith("'")) {

			ret = CompareUtil.compareString(l.toString(), r.toString());
		} else {

			ret = ByteUtil.compareNumberByte(l.toString().getBytes(),
					r.getBytes());

		}
		return ret;
	}

	public static int isNum(String cls) {
		int r = 0;

		if (cls.equals("java.lang.Integer") || cls.equals("java.lang.Double")
				|| cls.equals("java.lang.Float")
				|| cls.equals("java.lang.Long")
				|| cls.equals("java.lang.Short")
				|| cls.equals("java.lang.Number")
				|| cls.equals("java.math.BigDecimal")) {
			r = 1;
		}
		return r;
	}

	
	public static int getColType(Object obj) {
		String cls=obj.getClass().getName();
		int r = 0;
		switch(cls){
			case "java.lang.Integer" :return ColType.XINT;
			case "java.lang.Double" :return ColType.XDOUBLE;
			case "java.lang.Float" :return ColType.XFLOAT;
			case "java.lang.Long" :return ColType.XLONG;
			case "java.lang.short" :return ColType.XSHORT;
			case "java.lang.BigDecimal" :return ColType.XBIG;
			case "java.lang.String" :return ColType.XSTR;
		}
		 
		return r;
	}
	
	public static boolean matchOpr(int ret, String opx) {
		// System.out.println(">        " + opx + "  , " + ret);
		boolean r = false;
		if (opx.equals("=")) {
			if (ret == 0) {
				return true;
			} else {
				return false;
			}

		} else if (opx.equals(">")) {

			if (ret > 0) {
				return true;
			} else {
				return false;
			}

		} else if (opx.equals("<")) {
			if (ret < 0) {
				return true;
			} else {
				return false;
			}

		} else if (opx.equals("<>") || opx.equals("!=")) {
			if (ret != 0) {
				return true;
			} else {
				return false;
			}

		}
		return r;
	}
}
