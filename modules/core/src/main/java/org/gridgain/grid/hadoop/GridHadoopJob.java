/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.hadoop;

import org.gridgain.grid.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Hadoop job.
 */
public interface GridHadoopJob {
    /**
     * Gets job ID.
     *
     * @return Job ID.
     */
    public GridHadoopJobId id();

    /**
     * Gets job information.
     *
     * @return Job information.
     */
    public GridHadoopJobInfo info();

    /**
     * Gets collection of input splits for this job.
     *
     * @return Input splits.
     */
    public Collection<GridHadoopInputSplit> input() throws GridException;

    /**
     * Gets number of reducers for this job.
     *
     * @return Number of reducers.
     */
    public int reducers();

    /**
     * Checks whether job has combiner.
     *
     * @return {@code True} if job has combiner.
     */
    public boolean hasCombiner();

    /**
     * Gets partitioner for the job.
     *
     * @return Partitioner.
     */
    public GridHadoopPartitioner partitioner() throws GridException;

    /**
     * Creates new instance of key serialization object.
     *
     * @return Serialization facility.
     * @throws GridException if failed.
     */
    public GridHadoopSerialization keySerialization() throws GridException;

    /**
     * Creates new instance of value serialization object.
     *
     * @return Serialization facility.
     * @throws GridException if failed.
     */
    public GridHadoopSerialization valueSerialization() throws GridException;

    /**
     * Creates mapper output key sorting comparator.
     *
     * @return New sort comparator.
     */
    public Comparator<?> sortComparator();

    /**
     * Creates reducer key grouping comparator.
     *
     * @return New group comparator.
     */
    @Nullable public Comparator<?> reduceGroupComparator();

    /**
     * Creates combiner key grouping comparator.
     *
     * @return New group comparator.
     */
    @Nullable public Comparator<?> combineGroupComparator();

    /**
     * Creates task to be executed.
     *
     * @param taskInfo Task info.
     * @return Task.
     */
    public GridHadoopTask createTask(GridHadoopTaskInfo taskInfo);

    /**
     * Gets optional configuration property for the job.
     *
     * @param name Property name.
     * @return Value or {@code null} if none.
     */
    @Nullable public String property(String name);
}
