package asl.concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TaskThread<T> implements Runnable {
	private static final Logger logger = LoggerFactory
			.getLogger(asl.concurrent.TaskThread.class);

	private boolean running = false;
	private LinkedBlockingQueue<Task<T>> queue;
	private long timeout = -1;
	private TimeUnit unit;

	// constructor(s)
	public TaskThread() {
		queue = new LinkedBlockingQueue<Task<T>>();
	}

	public TaskThread(int capacity) {
		queue = new LinkedBlockingQueue<Task<T>>(capacity);
	}

	// timeout
	public void setTimeout(long timeout, TimeUnit unit) {
		this.timeout = timeout;
		this.unit = unit;
	}

	public long getTimeout() {
		return timeout;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public void addTask(String command, T data) throws InterruptedException {
		try {
			queue.put(new Task<T>(command, data));
		} catch (InterruptedException e) {
			throw e;
		}
	}

	// implements Runnable's run() method
	public void run() {
		setup();
		Task<T> task;
		running = true;
		while (running) {
			try {
				if (timeout < 0) {
					// Wait indefinitely if timeout is not specified
					task = queue.take();
				} else {
					// Otherwise wait for the duration specified
					task = queue.poll(timeout, unit);
				}

				// If we received a halt command, wrap-up the thread
				if ((task != null) && (task.getCommand() == "HALT")) {
					logger.debug("Halt requested.");
					running = false;
				}
				// Otherwise hand off the task
				else {
					logger.debug(String.format("Performing task %s : %s", task
							.getCommand(), (task.getData() == null) ? "null"
							: task.getData()));
					performTask(task);
				}
			} catch (InterruptedException e) {
				logger.warn("Caught InterruptedException:", e);
			}
		}
		cleanup();
	}

	// abstract methods
	protected abstract void setup();

	protected abstract void performTask(Task<T> data);

	protected abstract void cleanup();

	// halt
	public void halt() throws InterruptedException {
		try {
			halt(false);
		} catch (InterruptedException e) {
			throw e;
		}
	}

	public void halt(boolean now) throws InterruptedException {
		if (now) {
			running = false;
		}
		try {
			queue.put(new Task<T>("HALT", null));
		} catch (InterruptedException e) {
			throw e;
		}
	}
}
