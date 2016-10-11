package org.apache.dearbaby.util;

import java.math.BigDecimal;

/**
 * 数学计算工具类
 */
public class MathUtil {

    /**
     * 精确度
     * 值：6
     */
    public static int SCALE = 6;

    /**
     * 舍入模式
     * 值：BigDecimal.ROUND_HALF_EVEN
     */
    public static int ROUNDING_MODE = BigDecimal.ROUND_HALF_EVEN;

    public static final int MSHORT=4;
    public static final int MINT=5;
    public static final int MLONG=6;
    public static final int MFLOAT=7;
    public static final int MDOUB=8;
    public static final int MBIG=9;

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(int augend, int addend) {
        return add(Integer.toString(augend), Integer.toString(addend));
    }

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(long augend, long addend) {
        return add(Long.toString(augend), Long.toString(addend));
    }

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(float augend, float addend) {
        return add(Float.toString(augend), Float.toString(addend));
    }

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(double augend, double addend) {
        return add(Double.toString(augend), Double.toString(addend));
    }

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(String augend, String addend) {
        return add(new BigDecimal(augend), new BigDecimal(addend));
    }

    /**
     * 两数相加
     * @param augend 被加数
     * @param addend 加数
     * @return BigDecimal 两数相加计算结果
     */
    public static BigDecimal add(BigDecimal augend, BigDecimal addend) {
        if (augend == null || addend == null) {
            return null;
        }
        return augend.add(addend);
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(int minuend, int subtrahend) {
        return subtract(Integer.toString(minuend), Integer.toString(subtrahend));
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(long minuend, long subtrahend) {
        return subtract(Long.toString(minuend), Long.toString(subtrahend));
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(float minuend, float subtrahend) {
        return subtract(Float.toString(minuend), Float.toString(subtrahend));
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(double minuend, double subtrahend) {
        return subtract(Double.toString(minuend), Double.toString(subtrahend));
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(String minuend, String subtrahend) {
        return subtract(new BigDecimal(minuend), new BigDecimal(subtrahend));
    }

    /**
     * 两数相减
     * @param minuend 被减数
     * @param subtrahend 减数
     * @return BigDecimal 两数相减计算结果
     */
    public static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
        if (minuend == null || subtrahend == null) {
            return null;
        }
        return minuend.subtract(subtrahend);
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(int multiplicand, int multiplier) {
        return multiply(Integer.toString(multiplicand), Integer.toString(multiplier));
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(long multiplicand, long multiplier) {
        return multiply(Long.toString(multiplicand), Long.toString(multiplier));
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(float multiplicand, float multiplier) {
        return multiply(Float.toString(multiplicand), Float.toString(multiplier));
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(double multiplicand, double multiplier) {
        return multiply(Double.toString(multiplicand), Double.toString(multiplier));
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(String multiplicand, String multiplier) {
        return multiply(new BigDecimal(multiplicand), new BigDecimal(multiplier));
    }

    /**
     * 两数相乘
     * @param multiplicand 被乘数
     * @param multiplier 乘数
     * @return BigDecimal 两数相乘计算结果
     */
    public static BigDecimal multiply(BigDecimal multiplicand, BigDecimal multiplier) {
        if (multiplicand == null || multiplier == null) {
            return null;
        }
        return multiplicand.multiply(multiplier);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(int dividend, int divisor) {
        return divide(Integer.toString(dividend), Integer.toString(divisor));
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(long dividend, long divisor) {
        return divide(Long.toString(dividend), Long.toString(divisor));
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(float dividend, float divisor) {
        return divide(Float.toString(dividend), Float.toString(divisor));
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(double dividend, double divisor) {
        return divide(Double.toString(dividend), Double.toString(divisor));
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(String dividend, String divisor) {
        return divide(new BigDecimal(dividend), new BigDecimal(divisor), ROUNDING_MODE);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(int dividend, int divisor, int roundingMode) {
        return divide(Integer.toString(dividend), Integer.toString(divisor), roundingMode);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(long dividend, long divisor, int roundingMode) {
        return divide(Long.toString(dividend), Long.toString(divisor), roundingMode);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(float dividend, float divisor, int roundingMode) {
        return divide(Float.toString(dividend), Float.toString(divisor), roundingMode);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(double dividend, double divisor, int roundingMode) {
        return divide(Double.toString(dividend), Double.toString(divisor), roundingMode);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(String dividend, String divisor, int roundingMode) {
        return divide(new BigDecimal(dividend), new BigDecimal(divisor), roundingMode);
    }

    /**
     * 两数相除
     * @param dividend 被除数
     * @param divisor 除数
     * @param roundingMode 精确度
     * @return BigDecimal 两数相除计算结果
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, int roundingMode) {
        if (dividend == null || dividend == null) {
            return null;
        }
        return dividend.divide(divisor, SCALE, roundingMode);
    }

    public static int getRetType(Object obj1,Object obj2){
    	int t1=getNumType(obj1);
    	int t2=getNumType(obj2);
    	if(t1>t2){
    		return t1;
    	}
    	return t2;
    }
    
    public static int getNumType(Object obj){
    	String cls=obj.getClass().getName();
    	if (cls.equals("java.lang.Integer")) {
    		return MINT;
    	} 
    	
    	if (cls.equals("java.lang.Double")) {
    		return MDOUB;
    	} 
    	
    	if (cls.equals("java.lang.Float")) {
    		return MFLOAT;
    	} 
    	
    	if (cls.equals("java.lang.Long")) {
    		return MLONG;
    	} 
    	
    	if (cls.equals("java.lang.Short")) {
    		return MSHORT;
    	} 
    	if (cls.equals("java.math.BigDecimal")) {
    		return MBIG;
    	} 
    	return -1;
    }
    
    public static Object getNumVal(BigDecimal val,int type){
    	if(type==MINT){
    		return new Integer(val.toString());
    	}
    	if(type==MDOUB){
    		return new Double(val.toString());
    	}
    	if(type==MFLOAT){
    		return new Float(val.toString());
    	}
    	if(type==MLONG){
    		return new Long(val.toString());
    	}
    	if(type==MSHORT){
    		return new Short(val.toString());
    	}
    	if(type==MBIG){
    		return val;
    	}
    	return val;
    }
    
}
