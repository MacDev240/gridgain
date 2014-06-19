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
 * Map-reduce execution planner.
 */
public interface GridHadoopMapReducePlanner {
    /**
     * Prepares map-reduce execution plan for the given collection of input splits and topology.
     *
     * @param splits Input splits.
     * @param top Topology.
     * @param job Job.
     * @param oldPlan Old plan in case of partial failure.
     * @return Map reduce plan.
     */
    public GridHadoopMapReducePlan preparePlan(Collection<GridHadoopInputSplit> splits, Collection<GridNode> top,
        GridHadoopJob job, @Nullable GridHadoopMapReducePlan oldPlan) throws GridException;
}
