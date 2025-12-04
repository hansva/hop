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

import java.io.File;
import java.util.Set;
import org.apache.hop.core.logging.ConsoleLoggingEventListener;
import org.apache.hop.core.logging.HopLogStore;
import org.apache.hop.core.plugins.JarCache;
import org.jboss.jandex.Index;

/** Detailed startup profiler to identify specific bottlenecks in JAR scanning and index loading. */
public class DetailedStartupProfiler {

  public static void main(String[] args) throws Exception {
    System.out.println("=".repeat(80));
    System.out.println("Detailed Startup Profiler");
    System.out.println("=".repeat(80));

    // Initialize minimal logging
    System.setProperties(ConcurrentMapProperties.convertProperties(System.getProperties()));
    HopLogStore.init();
    HopLogStore.getAppender().addLoggingEventListener(new ConsoleLoggingEventListener());

    JarCache cache = JarCache.getInstance();

    // Profile native JAR scanning
    System.out.println("\n--- Native JAR Scanning ---");
    long start = System.currentTimeMillis();
    Set<File> nativeJars = cache.getNativeJars();
    long nativeTime = System.currentTimeMillis() - start;
    System.out.printf("Found %d native JARs in %d ms%n", nativeJars.size(), nativeTime);

    // Profile plugin JAR scanning with breakdown
    System.out.println("\n--- Plugin JAR Scanning ---");

    // Check if cache file exists
    File cacheFile = new File("config/jar-cache.index");
    System.out.printf(
        "Cache file exists: %s, size: %d bytes%n",
        cacheFile.exists(), cacheFile.exists() ? cacheFile.length() : 0);

    start = System.currentTimeMillis();
    Set<File> pluginJars = cache.getPluginJars();
    long pluginScanTime = System.currentTimeMillis() - start;
    System.out.printf("Found %d plugin JARs in %d ms%n", pluginJars.size(), pluginScanTime);

    // Check if we did file scanning by looking at the timing
    if (pluginScanTime > 100) {
      System.out.println("WARNING: Plugin scanning took long - cache may not have been used");
    }

    // Profile index loading for plugin JARs
    System.out.println("\n--- Index Loading Breakdown ---");
    int indexCount = 0;
    long totalIndexTime = 0;
    long maxIndexTime = 0;
    String slowestJar = "";

    for (File jarFile : pluginJars) {
      start = System.currentTimeMillis();
      Index index = cache.getIndex(jarFile);
      long indexTime = System.currentTimeMillis() - start;
      totalIndexTime += indexTime;

      if (index != null) {
        indexCount++;
        if (indexTime > maxIndexTime) {
          maxIndexTime = indexTime;
          slowestJar = jarFile.getName();
        }
        if (indexTime > 10) {
          System.out.printf("  Slow JAR: %s - %d ms%n", jarFile.getName(), indexTime);
        }
      }
    }

    System.out.printf("%nLoaded %d indexes in %d ms%n", indexCount, totalIndexTime);
    System.out.printf("Slowest JAR: %s (%d ms)%n", slowestJar, maxIndexTime);
    System.out.printf(
        "Average time per JAR: %.2f ms%n", (double) totalIndexTime / pluginJars.size());

    // Profile file system operations
    System.out.println("\n--- File System Analysis ---");
    int jarFilesInPlugins = 0;
    for (String folder : cache.getPluginFolders()) {
      File folderFile = new File(folder);
      if (folderFile.exists()) {
        jarFilesInPlugins += countJarFiles(folderFile);
      }
    }
    System.out.printf("Total JAR files in plugin folders: %d%n", jarFilesInPlugins);
    System.out.printf("JARs with Jandex index: %d%n", pluginJars.size());
    System.out.printf("JARs without index (skipped): %d%n", jarFilesInPlugins - pluginJars.size());

    // Summary
    System.out.println("\n" + "=".repeat(80));
    System.out.println("SUMMARY");
    System.out.println("=".repeat(80));
    System.out.printf("Native JAR scanning:  %6d ms%n", nativeTime);
    System.out.printf("Plugin JAR scanning:  %6d ms%n", pluginScanTime);
    System.out.printf("Index loading:        %6d ms%n", totalIndexTime);
    System.out.printf(
        "TOTAL:                %6d ms%n", nativeTime + pluginScanTime + totalIndexTime);

    // Recommendations
    System.out.println("\n" + "=".repeat(80));
    System.out.println("OPTIMIZATION OPPORTUNITIES");
    System.out.println("=".repeat(80));

    if (jarFilesInPlugins - pluginJars.size() > 50) {
      System.out.println("- Many JARs without Jandex index are being scanned unnecessarily");
      System.out.println("  Consider caching which JARs have indexes");
    }

    if (totalIndexTime > 200) {
      System.out.println("- Index loading is slow, consider caching Jandex indexes to disk");
    }

    if (maxIndexTime > 50) {
      System.out.println("- Some JARs are slow to load: " + slowestJar);
    }
  }

  private static int countJarFiles(File folder) {
    int count = 0;
    File[] files = folder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          count++;
        } else if (file.isDirectory() && !"lib".equals(file.getName())) {
          count += countJarFiles(file);
        }
      }
    }
    return count;
  }
}
