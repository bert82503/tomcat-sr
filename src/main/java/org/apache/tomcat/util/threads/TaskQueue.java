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

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 专门设计的运行在线程池执行器的任务队列。
 * <p>
 * As task queue specifically designed to run with a thread pool executor.
 * The task queue is optimised to properly utilize threads within
 * a thread pool executor. If you use a normal queue, the executor will spawn threads
 * when there are idle threads and you wont be able to force items unto the queue itself
 * @author fhanik
 *
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;

    /** 该任务队列所在的线程池执行器 */
    private ThreadPoolExecutor parent = null;

    // no need to be volatile, the one times when we change and read it occur in
    // a single thread (the one that did stop a context and fired listeners)
    private Integer forcedRemainingCapacity = null;

    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }

    public boolean force(Runnable o) {
        if (parent.isShutdown()) {
        	// 执行器已被关闭
        	throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        }
        return super.offer(o); // forces the item onto the queue, to be used if the task is rejected
    }

    /**
     * 强制将任务插入到队列尾部。
     * 
     * @param o 可运行的任务
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (parent.isShutdown()) {
        	// 执行器已被关闭
        	throw new RejectedExecutionException("Executor not running, can't force a command into the queue");
        }
        return super.offer(o, timeout, unit); // forces the item onto the queue, to be used if the task is rejected
    }

    /*
     * 插入指定元素到队列尾部。
     */
    @Override
    public boolean offer(Runnable o) {
    	//we can't do any checks
        if (parent==null) return super.offer(o);
        //we are maxed out on threads, simply queue the object (达到"最大线程数")
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);
        //we have idle threads, just add it to the queue (核心池有空闲线程)
        if (parent.getSubmittedCount() < parent.getPoolSize()) return super.offer(o);
        //if we have less threads than maximum force creation of a new thread (需要创建一个新的线程)
        if (parent.getPoolSize() < parent.getMaximumPoolSize()) return false;
        //if we reached here, we need to add it to the queue (插入到队列)
        return super.offer(o);
    }

    /*
     * 检索并移除队列头部任务。(非阻塞操作)
     */
    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks. (关闭当前的线程，以避免内存泄露)
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    /*
     * 检索并移除队列头部任务。(阻塞操作)
     */
    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
        	// 先尝试着在给定的时间里去检索任务
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        // 一直阻塞
        return super.take();
    }

    /*
     * 任务队列的剩余容量。
     */
    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity != null) {
            // ThreadPoolExecutor.setCorePoolSize checks that
            // remainingCapacity==0 to allow to interrupt idle threads
            // I don't see why, but this hack allows to conform to this
            // "requirement"
            return forcedRemainingCapacity.intValue();
        }
        return super.remainingCapacity();
    }

    public void setForcedRemainingCapacity(Integer forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

}
