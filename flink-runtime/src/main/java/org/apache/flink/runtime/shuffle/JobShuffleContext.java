/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.shuffle;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Job level shuffle context which can offer some job information like job ID and through it, the
 * shuffle plugin notify the job to stop tracking the lost result partitions.
 */
public interface JobShuffleContext {

    /** @return the corresponding {@link JobID}. */
    JobID getJobId();

    /**
     * Notifies the job to stop tracking and release the target result partitions, which means these
     * partitions will be removed and will be reproduced if used afterwards.
     */
    CompletableFuture<?> stopTrackingAndReleasePartitions(
            Collection<ResultPartitionID> partitionIds);

    /**
     * Retrieves a collection containing descriptions and metrics of existing result partitions from
     * all TaskManagers.
     */
    CompletableFuture<Collection<PartitionWithMetrics>> getAllPartitionWithMetricsOnTaskManagers();
}
