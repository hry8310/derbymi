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
				|| cls.equals("java.lang.Number")) {
			r = 1;
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
