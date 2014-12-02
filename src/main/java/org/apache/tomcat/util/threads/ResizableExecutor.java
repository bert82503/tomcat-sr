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

import java.util.concurrent.Executor;

/**
 * "基于并发执行器({@link Executor})的可变大小执行器"接口
 */
public interface ResizableExecutor extends Executor {

    /**
     * Returns the current number of threads in the pool.
     * <br>
     * 返回线程池中线程的当前数量。
     *
     * @return the number of threads
     */
    public int getPoolSize();

    /**
     * 返回该线程池能持有的最大线程数。
     * 
     * @return
     */
    public int getMaxThreads();

    /**
     * Returns the approximate number of threads that are actively executing tasks.
     * <br>
     * 返回正在执行任务的线程的近似数量。
     *
     * @return the number of threads
     */
    public int getActiveCount();

    /**
     * 调整线程池的核心线程数和最大线程数。
     * 
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @return
     */
    public boolean resizePool(int corePoolSize, int maximumPoolSize);

    /**
     * 调整任务工作(阻塞)队列的容量。
     * 
     * @param capacity
     * @return
     */
    public boolean resizeQueue(int capacity);

}
