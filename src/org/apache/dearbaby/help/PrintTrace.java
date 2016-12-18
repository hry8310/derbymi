package org.apache.dearbaby.help;

public class PrintTrace {
 
		public static void print(String key) {
			Throwable ex = new Throwable();
			StackTraceElement[] stackElements = ex.getStackTrace();
			if (stackElements != null) {
				System.out.println("---------- " + key);
				for (int i = 1; i < stackElements.length; i++) {
					System.out.print(stackElements[i].getClassName() + " , ");
					System.out.print(stackElements[i].getMethodName() + " , ");
					System.out.println(stackElements[i].getLineNumber() + "");

				}
				System.out.println("======================================");
			}
		}

		public static void print() {
			Throwable ex = new Throwable();
			StackTraceElement[] stackElements = ex.getStackTrace();
			if (stackElements != null) {
				System.out.println("-------------------------------------");
				for (int i = 1; i < stackElements.length; i++) {
					System.out.print(stackElements[i].getClassName() + " , ");
					System.out.print(stackElements[i].getMethodName() + " , ");
					System.out.println(stackElements[i].getLineNumber() + "");

				}
				System.out.println("======================================");
			}
		}

		public static void prints(byte[] bArray) {
			String a = "";
			String hex = "";
			for (int i = 0; i < bArray.length; i++) {
				String sTemp = Integer.toString(bArray[i]);
				a = a + "," + sTemp;
			}
			System.out.println("a   " + a);
		}
	}

 
