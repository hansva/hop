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

package org.apache.hop.core.plugins;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hop.core.Const;
import org.apache.hop.i18n.LanguageChoice;

/**
 * Persistent cache for plugin translations. Stores translations to disk to avoid expensive
 * ResourceBundle lookups on subsequent startups.
 *
 * <p>The cache is locale-aware - if the locale changes, the cache is invalidated.
 */
public class TranslationCache {

  private static final String CACHE_FILE_NAME = "translation-cache.index";
  private static final String CACHE_VERSION = "1";

  private static TranslationCache instance;

  // In-memory cache: key -> translated value
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  // Track if cache was modified and needs saving
  private final AtomicBoolean dirty = new AtomicBoolean(false);

  // Track if cache was loaded from disk
  private boolean loaded = false;

  // The locale this cache was built for
  private String cachedLocale;

  private TranslationCache() {
    // No shutdown hook - save is called explicitly or periodically
  }

  public static synchronized TranslationCache getInstance() {
    if (instance == null) {
      instance = new TranslationCache();
    }
    return instance;
  }

  /**
   * Get a cached translation. Returns null if not in cache.
   *
   * @param key the translation key
   * @return the cached translation, or null if not found
   */
  public String get(String key) {
    ensureLoaded();
    return cache.get(key);
  }

  /**
   * Store a translation in the cache.
   *
   * @param key the translation key
   * @param value the translated value
   */
  public void put(String key, String value) {
    ensureLoaded();
    String existing = cache.put(key, value);
    if (existing == null || !existing.equals(value)) {
      dirty.set(true);
    }
  }

  /** Ensure the cache is loaded from disk */
  private synchronized void ensureLoaded() {
    if (!loaded) {
      loadFromDisk();
      loaded = true;
    }
  }

  /** Get the cache file location in HOP_AUDIT_FOLDER/caches */
  private File getCacheFile() {
    File cacheDir = new File(Const.HOP_AUDIT_FOLDER, "caches");
    return new File(cacheDir, CACHE_FILE_NAME);
  }

  /** Get the current locale string for cache validation */
  private String getCurrentLocale() {
    try {
      Locale locale = LanguageChoice.getInstance().getDefaultLocale();
      return locale != null ? locale.toString() : Locale.getDefault().toString();
    } catch (Exception e) {
      return Locale.getDefault().toString();
    }
  }

  /** Load the cache from disk. Note: no logging here to avoid circular initialization. */
  private void loadFromDisk() {
    File cacheFile = getCacheFile();
    if (!cacheFile.exists()) {
      cachedLocale = getCurrentLocale();
      return;
    }

    try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
      // Read version
      String version = reader.readLine();
      if (!CACHE_VERSION.equals(version)) {
        // Version mismatch - rebuild cache
        cachedLocale = getCurrentLocale();
        return;
      }

      // Read locale
      String savedLocale = reader.readLine();
      String currentLocale = getCurrentLocale();
      if (!currentLocale.equals(savedLocale)) {
        // Locale changed - rebuild cache
        cachedLocale = currentLocale;
        return;
      }
      cachedLocale = savedLocale;

      // Read translations
      String line;
      while ((line = reader.readLine()) != null) {
        int separatorIndex = line.indexOf('\t');
        if (separatorIndex > 0) {
          String key = line.substring(0, separatorIndex);
          String value = line.substring(separatorIndex + 1);
          // Unescape newlines and tabs in value
          value = value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
          cache.put(key, value);
        }
      }

    } catch (IOException e) {
      // Silently ignore - cache will be rebuilt
      cachedLocale = getCurrentLocale();
    }
  }

  /** Save the cache to disk */
  private synchronized void saveToDisk() {
    if (cache.isEmpty()) {
      return;
    }

    File cacheFile = getCacheFile();
    try {
      // Ensure parent directory exists
      File parentDir = cacheFile.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs();
      }

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
        // Write version
        writer.write(CACHE_VERSION);
        writer.newLine();

        // Write locale
        writer.write(cachedLocale != null ? cachedLocale : getCurrentLocale());
        writer.newLine();

        // Write translations (key\tvalue format, escaping special chars)
        for (Map.Entry<String, String> entry : cache.entrySet()) {
          String value = entry.getValue();
          // Escape backslashes, newlines, and tabs in value
          value = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
          writer.write(entry.getKey() + "\t" + value);
          writer.newLine();
        }
      }

      dirty.set(false);

    } catch (IOException e) {
      // Silently ignore save errors
    }
  }

  /** Force save the cache to disk immediately */
  public void flush() {
    if (dirty.get()) {
      saveToDisk();
    }
  }

  /** Clear the cache (both in-memory and on disk) */
  public void clear() {
    cache.clear();
    dirty.set(false);
    File cacheFile = getCacheFile();
    if (cacheFile.exists()) {
      cacheFile.delete();
    }
  }

  /** Get the number of cached translations */
  public int size() {
    return cache.size();
  }
}
