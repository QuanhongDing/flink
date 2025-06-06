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

package org.apache.flink.events.slf4j;

import org.apache.flink.events.Event;
import org.apache.flink.events.reporter.EventReporter;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.traces.Span;
import org.apache.flink.traces.reporter.TraceReporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link TraceReporter} that exports {@link Span Spans} via SLF4J {@link Logger}. */
public class Slf4jEventReporter implements EventReporter {
    private static final Logger LOG = LoggerFactory.getLogger(Slf4jEventReporter.class);

    @Override
    public void open(MetricConfig metricConfig) {}

    @Override
    public void close() {}

    @Override
    public void notifyOfAddedEvent(Event event) {
        LOG.info("Reported event: {}", event);
    }
}
