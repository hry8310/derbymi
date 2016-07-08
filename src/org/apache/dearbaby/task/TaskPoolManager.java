package org.apache.dearbaby.task;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskPoolManager {
	private static ThreadPoolExecutor poolExecutor=null;
	private static int size = 0;
	private static final int incSize = 5;

	private static void newPool(int _size) {
		if (_size == 0) {
			return;
		}
		if (_size < 30) {
			_size = 30;
		}

	
		size = _size;
		poolExecutor = new ThreadPoolExecutor(size, size * 4, 60L,
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
				new NamedThreadFactory("Async Worker"));

	}

	private static void renewPool(int _size) {
		if (_size == 0) {
			return;
		}

		List<Runnable> list = null;
		if (poolExecutor != null) {
			list = poolExecutor.shutdownNow();
		}
		newPool(_size);
		submit(list);
	}

	public static void refixPool() {
		int qSize = poolExecutor.getQueue().size();
		if (qSize > size) {
			synchronized(TaskPoolManager.class){
				size = incSize + size;
				poolExecutor.setCorePoolSize(size);
				poolExecutor.setMaximumPoolSize(size * 4);
			}
		}
	}

	private static void submit(List<Runnable> list) {
		if (list != null) {
			for (Runnable r : list) {
				System.out.println("r" + r.getClass());
				poolExecutor.submit(r);
			}
		}
	}
	
	private static void submit( Runnable r) {
		 poolExecutor.submit(r);
	}
	
	public static void putTask(Runnable r) {
		if(poolExecutor == null){
			synchronized(TaskPoolManager.class){
				if (poolExecutor == null) {
				newPool(30);
				}
			}
		}
		submit(r);
	}

	public static void putTask(List tasks) {
		if (tasks.size() == 0) {
			return;
		}
		if (size < tasks.size() - 3) {
			renewPool(tasks.size());
		}
		if(poolExecutor == null){
			synchronized(TaskPoolManager.class){
				if (poolExecutor == null) {
				newPool(tasks.size());
				}
			}
		}
		refixPool();
		 
		submit(tasks);
	}

	static class NamedThreadFactory implements ThreadFactory {
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(0);
		private final String namePrefix;

		NamedThreadFactory(String name_prefix) {
			SecurityManager s = System.getSecurityManager();
			group = (s != null) ? s.getThreadGroup() : Thread.currentThread()
					.getThreadGroup();
			namePrefix = name_prefix;
		}

		public Thread newThread(Runnable r) {
			int id = threadNumber.getAndIncrement();
			Thread t = new MyThread(group, r, id);
			t.setName(namePrefix + "(PID:" + t.getId() + ") #" + id);
			if (t.isDaemon()) {
				t.setDaemon(false);
			}
			if (t.getPriority() != Thread.NORM_PRIORITY) {
				t.setPriority(Thread.NORM_PRIORITY);
			}
			return t;
		}
	}

	private static class MyThread extends Thread {
		private int threadId;

		public MyThread(ThreadGroup group, Runnable target, int aids) {
			super(group, target);
			threadId = aids;
			setDaemon(true);
		}

		public int getThreadId() {
			return threadId;
		}
	}
}
