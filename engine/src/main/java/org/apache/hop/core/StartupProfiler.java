/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.hop.core.auth.AuthenticationConsumerPluginType;
import org.apache.hop.core.auth.AuthenticationProviderPluginType;
import org.apache.hop.core.compress.CompressionPluginType;
import org.apache.hop.core.config.plugin.ConfigPluginType;
import org.apache.hop.core.database.DatabasePluginType;
import org.apache.hop.core.encryption.TwoWayPasswordEncoderPluginType;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.logging.ConsoleLoggingEventListener;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.logging.LoggingPluginType;
import org.apache.hop.core.plugins.ActionPluginType;
import org.apache.hop.core.plugins.HopServerPluginType;
import org.apache.hop.core.plugins.IPluginType;
import org.apache.hop.core.plugins.JarCache;
import org.apache.hop.core.plugins.PartitionerPluginType;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.plugins.TransformPluginType;
import org.apache.hop.core.row.value.ValueMetaPluginType;
import org.apache.hop.core.variables.VariableRegistry;
import org.apache.hop.core.variables.resolver.VariableResolverPluginType;
import org.apache.hop.core.vfs.plugin.VfsPluginType;
import org.apache.hop.execution.plugin.ExecutionInfoLocationPluginType;
import org.apache.hop.execution.sampler.ExecutionDataSamplerPluginType;
import org.apache.hop.hop.plugin.HopCommandPluginType;
import org.apache.hop.imp.ImportPluginType;
import org.apache.hop.metadata.plugin.MetadataPluginType;
import org.apache.hop.pipeline.engine.PipelineEnginePluginType;
import org.apache.hop.pipeline.transform.RowDistributionPluginType;
import org.apache.hop.workflow.engine.WorkflowEnginePluginType;

/**
 * Startup profiler for Apache Hop to identify performance bottlenecks during initialization. Run
 * this class to see detailed timing of each startup phase.
 */
public class StartupProfiler {

  private static final List<TimingRecord> timings = new ArrayList<>();

  public static void main(String[] args) throws HopException {
    System.out.println("=".repeat(80));
    System.out.println("Apache Hop Startup Profiler");
    System.out.println("=".repeat(80));
    System.out.println();

    long totalStart = System.currentTimeMillis();

    // Phase 1: System properties setup
    profile(
        "System Properties Setup",
        () -> {
          System.setProperties(ConcurrentMapProperties.convertProperties(System.getProperties()));
        });

    // Phase 2: HopLogStore initialization
    profile(
        "HopLogStore.init()",
        () -> {
          HopLogStore.init();
          HopLogStore.getAppender().addLoggingEventListener(new ConsoleLoggingEventListener());
        });

    // Phase 3: JarCache - Native jars scanning
    profile(
        "JarCache - Native jars scanning",
        () -> {
          JarCache.getInstance().getNativeJars();
        });

    // Phase 4: JarCache - Plugin jars scanning
    profile(
        "JarCache - Plugin jars scanning",
        () -> {
          JarCache.getInstance().getPluginJars();
        });

    // Phase 5: Client plugin types registration
    System.out.println("\n--- Client Plugin Types Registration ---");
    List<IPluginType> clientPluginTypes =
        List.of(
            LoggingPluginType.getInstance(),
            ValueMetaPluginType.getInstance(),
            DatabasePluginType.getInstance(),
            ExtensionPointPluginType.getInstance(),
            TwoWayPasswordEncoderPluginType.getInstance(),
            VariableResolverPluginType.getInstance(),
            VfsPluginType.getInstance());

    for (IPluginType pluginType : clientPluginTypes) {
      profilePluginType(pluginType);
    }

    // Phase 6: Standard plugin types registration
    System.out.println("\n--- Standard Plugin Types Registration ---");
    List<IPluginType> standardPluginTypes =
        List.of(
            RowDistributionPluginType.getInstance(),
            TransformPluginType.getInstance(),
            PartitionerPluginType.getInstance(),
            ActionPluginType.getInstance(),
            HopServerPluginType.getInstance(),
            CompressionPluginType.getInstance(),
            AuthenticationProviderPluginType.getInstance(),
            AuthenticationConsumerPluginType.getInstance(),
            PipelineEnginePluginType.getInstance(),
            WorkflowEnginePluginType.getInstance(),
            ConfigPluginType.getInstance(),
            MetadataPluginType.getInstance(),
            ImportPluginType.getInstance(),
            ExecutionDataSamplerPluginType.getInstance(),
            ExecutionInfoLocationPluginType.getInstance(),
            HopCommandPluginType.getInstance());

    for (IPluginType pluginType : standardPluginTypes) {
      profilePluginType(pluginType);
    }

    // Phase 7: Variable Registry
    profile("VariableRegistry.init()", VariableRegistry::init);

    long totalEnd = System.currentTimeMillis();

    // Print summary
    printSummary(totalEnd - totalStart);
  }

  private static void profile(String name, RunnableWithException runnable) {
    long start = System.currentTimeMillis();
    try {
      runnable.run();
    } catch (Exception e) {
      System.err.println("Error in " + name + ": " + e.getMessage());
    }
    long duration = System.currentTimeMillis() - start;
    timings.add(new TimingRecord(name, duration));
    System.out.printf("  %-50s %6d ms%n", name, duration);
  }

  private static void profilePluginType(IPluginType pluginType) {
    String name = pluginType.getName();
    long start = System.currentTimeMillis();
    try {
      PluginRegistry.addPluginType(pluginType);
      PluginRegistry.getInstance().registerType(pluginType);
    } catch (Exception e) {
      System.err.println("Error registering " + name + ": " + e.getMessage());
    }
    long duration = System.currentTimeMillis() - start;
    int pluginCount = PluginRegistry.getInstance().getPlugins(pluginType.getClass()).size();
    timings.add(new TimingRecord(name + " (" + pluginCount + " plugins)", duration));
    System.out.printf("  %-50s %6d ms  (%d plugins)%n", name, duration, pluginCount);
  }

  private static void printSummary(long totalTime) {
    System.out.println();
    System.out.println("=".repeat(80));
    System.out.println("SUMMARY - Sorted by Duration (Descending)");
    System.out.println("=".repeat(80));

    // Sort by duration descending
    timings.sort(Comparator.comparingLong(TimingRecord::duration).reversed());

    // Print top 15
    int count = 0;
    for (TimingRecord timing : timings) {
      if (count++ >= 15) break;
      double percentage = (timing.duration * 100.0) / totalTime;
      System.out.printf(
          "%2d. %-50s %6d ms  (%5.1f%%)%n", count, timing.name, timing.duration, percentage);
    }

    System.out.println("-".repeat(80));
    System.out.printf("TOTAL STARTUP TIME: %d ms (%.2f seconds)%n", totalTime, totalTime / 1000.0);
    System.out.println("=".repeat(80));

    // Print analysis
    System.out.println();
    System.out.println("ANALYSIS:");
    System.out.println("-".repeat(80));

    long pluginScanTime =
        timings.stream()
            .filter(t -> t.name.contains("plugins)"))
            .mapToLong(TimingRecord::duration)
            .sum();
    long jarCacheTime =
        timings.stream()
            .filter(t -> t.name.contains("JarCache"))
            .mapToLong(TimingRecord::duration)
            .sum();

    System.out.printf(
        "Plugin scanning time: %d ms (%.1f%%)%n",
        pluginScanTime, (pluginScanTime * 100.0) / totalTime);
    System.out.printf(
        "JarCache time: %d ms (%.1f%%)%n", jarCacheTime, (jarCacheTime * 100.0) / totalTime);
    System.out.printf(
        "Other: %d ms (%.1f%%)%n",
        totalTime - pluginScanTime - jarCacheTime,
        ((totalTime - pluginScanTime - jarCacheTime) * 100.0) / totalTime);
  }

  @FunctionalInterface
  interface RunnableWithException {
    void run() throws Exception;
  }

  record TimingRecord(String name, long duration) {}
}
