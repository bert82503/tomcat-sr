/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.util.res.StringManager;

/**
 * Same as a java.util.concurrent.ThreadPoolExecutor but implements a much more efficient
 * {@link #getSubmittedCount()} method, to be used to properly handle the work queue.
 * 
 * <p>If a RejectedExecutionHandler is not specified a default one will be configured
 * and that one will always throw a RejectedExecutionException
 * <p>
 * 与JDK的{@link java.util.concurrent.ThreadPoolExecutor ThreadPoolExecutor}实现同样的功能
 * (因为直接继承自它)，但实现了一个更高效的{@link #getSubmittedCount()}方法，用于处理任务工作队列。
 * 
 * @author fhanik
 */
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {

	protected static final StringManager sm = StringManager.getManager("org.apache.tomcat.util.threads.res");

    /**
     * 已提交但未完成的任务数，其包括队列中的任务和已被工作者线程处理但还未开始执行的任务。
     * <p>
     * The number of tasks submitted but not yet finished. This includes tasks
     * in the queue and tasks that have been handed to a worker thread but the
     * latter did not start executing the task yet.
     * This number is always greater or equal to {@link #getActiveCount()}.
     */
    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicLong lastContextStoppedTime = new AtomicLong(0L);

    /**
     * Most recent time in ms when a thread decided to kill itself to avoid
     * potential memory leaks. Useful to throttle the rate of renewals of
     * threads.
     */
    private final AtomicLong lastTimeThreadKilledItself = new AtomicLong(0L);

    /**
     * Delay in ms between 2 threads being renewed. If negative, do not renew threads.
     */
    private long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    /**
     * 创建一个Tomcat线程池执行器。
     * 
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 线程池持有的最大线程数
     * @param keepAliveTime 存活时间
     * @param unit
     * @param workQueue 任务工作队列
     * @param handler 拒绝执行处理器
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    /**
     * 创建一个Tomcat线程池执行器。
     * 
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param threadFactory 线程工厂
     * @param handler
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * 创建一个基于拒绝处理器的Tomcat线程池执行器。
     * 
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param threadFactory
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
    }

    /**
     * 创建一个基于拒绝处理器的Tomcat线程池执行器。
     * 
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new RejectHandler());
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submittedCount.decrementAndGet();

        if (t == null) {
            stopCurrentThreadIfNeeded();
        }
    }

    /**
     * If the current thread was started before the last time when a context was
     * stopped, an exception is thrown so that the current thread is stopped.
     */
    protected void stopCurrentThreadIfNeeded() {
        if (currentThreadShouldBeStopped()) {
            long lastTime = lastTimeThreadKilledItself.longValue();
            if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
                if (lastTimeThreadKilledItself.compareAndSet(lastTime,
                        System.currentTimeMillis() + 1)) {
                    // OK, it's really time to dispose of this thread

                    final String msg = sm.getString(
                                    "threadPoolExecutor.threadStoppedToAvoidPotentialLeak",
                                    Thread.currentThread().getName());

                    throw new StopPooledThreadException(msg);
                }
            }
        }
    }

    protected boolean currentThreadShouldBeStopped() {
        if (threadRenewalDelay >= 0
            && Thread.currentThread() instanceof TaskThread) {
            TaskThread currentTaskThread = (TaskThread) Thread.currentThread();
            if (currentTaskThread.getCreationTime() <
                    this.lastContextStoppedTime.longValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回已提交但未完成的任务数。
     * 
     * @return
     */
    public int getSubmittedCount() {
        return submittedCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        execute(command,0,TimeUnit.MILLISECONDS);
    }

    /**
     * 在未来的某一时刻，执行给定的命令。<br>
     * 命令可能是在一个新的线程中执行，也可能在一个连接池的线程中执行，或者在调用线程中执行。
     * <p>
     * 如果没有线程可用，它会被插入到任务工作队列中。<br>
     * 如果任务工作队列满了，系统会等待一定的时间。在等待这段时间后，
     * 如果队列仍然是满的，就会抛出一个"被拒绝执行的异常"。
     * <p>
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the <tt>Executor</tt> implementation.
     * 
     * If no threads are available, it will be added to the work queue.
     * If the work queue is full, the system will wait for the specified
     * time and it throw a RejectedExecutionException if the queue is still
     * full after that.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be
     * accepted for execution - the queue is full
     * @throws NullPointerException if command or unit is null
     */
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        submittedCount.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecutionException rx) { // 第一次被拒绝执行
            if (super.getQueue() instanceof TaskQueue) { // 如果工作队列是任务队列
                final TaskQueue queue = (TaskQueue) super.getQueue();
                try {
                    if (!queue.force(command, timeout, unit)) { // 能否强制将任务插入到阻塞队列中
                    	// 强制插入失败，抛出"队列容量满了"的拒绝执行异常！
                        submittedCount.decrementAndGet();
                        throw new RejectedExecutionException("Queue capacity is full.");
                    }
                } catch (InterruptedException x) {// 出现"线程中断"异常
                    submittedCount.decrementAndGet();
                    // 标记线程中断状态
                    Thread.interrupted();
                    throw new RejectedExecutionException(x);
                }
            } else {
                submittedCount.decrementAndGet();
                throw rx;
            }

        }
    }

    public void contextStopping() {
        this.lastContextStoppedTime.set(System.currentTimeMillis());

        // save the current pool parameters to restore them later
        int savedCorePoolSize = this.getCorePoolSize();
        TaskQueue taskQueue =
                getQueue() instanceof TaskQueue ? (TaskQueue) getQueue() : null;
        if (taskQueue != null) {
            // note by slaurent : quite oddly threadPoolExecutor.setCorePoolSize
            // checks that queue.remainingCapacity()==0. I did not understand
            // why, but to get the intended effect of waking up idle threads, I
            // temporarily fake this condition.
            taskQueue.setForcedRemainingCapacity(Integer.valueOf(0));
        }

        // setCorePoolSize(0) wakes idle threads
        this.setCorePoolSize(0);

        // TaskQueue.take() takes care of timing out, so that we are sure that
        // all threads of the pool are renewed in a limited time, something like
        // (threadKeepAlive + longest request time)

        if (taskQueue != null) {
            // ok, restore the state of the queue and pool
            taskQueue.setForcedRemainingCapacity(null);
        }
        this.setCorePoolSize(savedCorePoolSize);
    }

    /**
     * 拒绝处理器
     */
    private static class RejectHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r,
                java.util.concurrent.ThreadPoolExecutor executor) {
        	// 抛出"拒绝执行异常"
            throw new RejectedExecutionException();
        }

    }

}
