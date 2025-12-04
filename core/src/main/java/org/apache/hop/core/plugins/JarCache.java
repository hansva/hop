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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopFileException;
import org.apache.hop.core.logging.LogChannel;
import org.apache.hop.core.util.EnvUtil;
import org.apache.hop.core.variables.Variables;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;

/**
 * Cache for JAR files containing plugin annotations. This class maintains an in-memory cache and
 * persists to disk using a single combined binary file for fast loading.
 *
 * <p>The cache format stores JAR paths, modification times, AND Jandex indexes all in one file,
 * allowing everything to be loaded in a single read operation.
 */
public class JarCache {

  public static final String ANNOTATION_INDEX_LOCATION = "META-INF/jandex.idx";
  private static final String PLUGIN_CACHE_FILE = "plugin-cache.bin";
  private static final String NATIVE_CACHE_FILE = "native-cache.bin";
  private static final int CACHE_VERSION = 4;

  private static JarCache instance;

  // In-memory Jandex index cache - key is absolute path
  private final Map<String, Index> indexCache;

  // JAR file lists
  private final Set<File> nativeFiles;
  private final Set<File> pluginFiles;

  // For finding JARs in plugin folders
  private final Map<File, Set<File>> jarFilesPerFolder;

  // State flags
  private boolean nativeCacheLoaded = false;
  private boolean pluginCacheLoaded = false;

  // Cached plugin folders
  private List<String> cachedPluginFolders;

  private JarCache() {
    nativeFiles = new HashSet<>();
    pluginFiles = new HashSet<>();
    jarFilesPerFolder = new HashMap<>();
    indexCache = new HashMap<>();
  }

  public static JarCache getInstance() {
    if (instance == null) {
      instance = new JarCache();
    }
    return instance;
  }

  // ==================== Cache Directory ====================

  private File getCacheBaseDir() {
    return new File(Const.HOP_AUDIT_FOLDER, "caches");
  }

  private void ensureCacheDir() {
    File dir = getCacheBaseDir();
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  // ==================== Plugin Folders ====================

  public List<String> getPluginFolders() {
    if (cachedPluginFolders != null) {
      return cachedPluginFolders;
    }

    List<String> pluginFolders = new ArrayList<>();
    String folderPaths = EnvUtil.getSystemProperty(Const.HOP_PLUGIN_BASE_FOLDERS);
    if (folderPaths == null) {
      folderPaths = Variables.getADefaultVariableSpace().getVariable(Const.HOP_PLUGIN_BASE_FOLDERS);
    }
    if (folderPaths == null) {
      folderPaths = Const.DEFAULT_PLUGIN_BASE_FOLDERS;
    }

    for (String folder : folderPaths.split(",")) {
      pluginFolders.add(folder.trim());
    }

    cachedPluginFolders = pluginFolders;
    return pluginFolders;
  }

  // ==================== Native JARs ====================

  public Set<File> getNativeJars() throws HopFileException {
    if (!nativeFiles.isEmpty()) {
      return nativeFiles;
    }

    // Try loading from combined cache file
    if (loadNativeCache()) {
      return nativeFiles;
    }

    // Cache miss - scan and build cache
    scanNativeJars();
    saveNativeCache();

    return nativeFiles;
  }

  private boolean loadNativeCache() {
    if (nativeCacheLoaded) {
      return !nativeFiles.isEmpty();
    }

    File cacheFile = new File(getCacheBaseDir(), NATIVE_CACHE_FILE);
    if (!cacheFile.exists()) {
      return false;
    }

    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {

      // Read and validate version
      int version = in.readInt();
      if (version != CACHE_VERSION) {
        nativeCacheLoaded = true;
        return false;
      }

      // Read and validate classpath hash
      int savedHash = in.readInt();
      int currentHash = System.getProperty("java.class.path").hashCode();
      if (savedHash != currentHash) {
        nativeCacheLoaded = true;
        return false;
      }

      // Read number of entries
      int numEntries = in.readInt();

      // Read all entries
      for (int i = 0; i < numEntries; i++) {
        String path = in.readUTF();
        long modTime = in.readLong();
        int indexSize = in.readInt();
        byte[] indexBytes = new byte[indexSize];
        in.readFully(indexBytes);

        File jarFile = new File(path);

        // Validate file exists and hasn't changed
        if (!jarFile.exists() || jarFile.lastModified() != modTime) {
          // Cache invalid
          nativeFiles.clear();
          indexCache.clear();
          nativeCacheLoaded = true;
          return false;
        }

        // Parse the index
        Index index = new IndexReader(new ByteArrayInputStream(indexBytes)).read();

        nativeFiles.add(jarFile);
        indexCache.put(path, index);
      }

      nativeCacheLoaded = true;
      return true;

    } catch (Exception e) {
      LogChannel.GENERAL.logDebug("Failed to load native cache: " + e.getMessage());
      nativeFiles.clear();
      indexCache.clear();
      return false;
    }
  }

  private void scanNativeJars() throws HopFileException {
    try {
      Enumeration<URL> indexUrls =
          getClass().getClassLoader().getResources(ANNOTATION_INDEX_LOCATION);

      while (indexUrls.hasMoreElements()) {
        URL url = indexUrls.nextElement();
        File jarFile = urlToFile(url);
        if (jarFile != null) {
          // Load index directly from URL
          try (InputStream stream = url.openStream()) {
            Index index = new IndexReader(stream).read();
            nativeFiles.add(jarFile);
            indexCache.put(jarFile.getAbsolutePath(), index);
          }
        }
      }
    } catch (Exception e) {
      throw new HopFileException("Error scanning native JARs", e);
    }
  }

  private void saveNativeCache() {
    ensureCacheDir();
    File cacheFile = new File(getCacheBaseDir(), NATIVE_CACHE_FILE);

    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)))) {

      // Write version
      out.writeInt(CACHE_VERSION);

      // Write classpath hash
      out.writeInt(System.getProperty("java.class.path").hashCode());

      // Write number of entries
      out.writeInt(nativeFiles.size());

      // Write each entry with its index
      for (File jarFile : nativeFiles) {
        String path = jarFile.getAbsolutePath();
        Index index = indexCache.get(path);

        out.writeUTF(path);
        out.writeLong(jarFile.lastModified());

        // Serialize index to bytes
        ByteArrayOutputStream indexBytes = new ByteArrayOutputStream();
        new IndexWriter(indexBytes).write(index);
        byte[] bytes = indexBytes.toByteArray();

        out.writeInt(bytes.length);
        out.write(bytes);
      }

    } catch (Exception e) {
      LogChannel.GENERAL.logDebug("Failed to save native cache: " + e.getMessage());
    }
  }

  private File urlToFile(URL url) {
    String path = url.toString();
    if (path.startsWith("jar:")) {
      path = path.substring(4);
    }
    int bang = path.indexOf("!/");
    if (bang > 0) {
      path = path.substring(0, bang);
    }
    if (path.startsWith("file:")) {
      path = path.substring(5);
    }
    try {
      path = java.net.URLDecoder.decode(path, "UTF-8");
    } catch (Exception e) {
      // Ignore
    }
    return new File(path);
  }

  // ==================== Plugin JARs ====================

  public Set<File> getPluginJars() throws HopFileException {
    if (!pluginFiles.isEmpty()) {
      return pluginFiles;
    }

    // Try loading from combined cache file
    if (loadPluginCache()) {
      return pluginFiles;
    }

    // Cache miss - scan and build cache
    scanPluginJars();
    savePluginCache();

    return pluginFiles;
  }

  private boolean loadPluginCache() {
    if (pluginCacheLoaded) {
      return !pluginFiles.isEmpty();
    }

    File cacheFile = new File(getCacheBaseDir(), PLUGIN_CACHE_FILE);
    if (!cacheFile.exists()) {
      return false;
    }

    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {

      // Read and validate version
      int version = in.readInt();
      if (version != CACHE_VERSION) {
        pluginCacheLoaded = true;
        return false;
      }

      // Read and validate folders hash
      int savedHash = in.readInt();
      int currentHash = getPluginFolders().hashCode();
      if (savedHash != currentHash) {
        pluginCacheLoaded = true;
        return false;
      }

      // Quick validation: check cache file age vs folder mod times
      long cacheModTime = cacheFile.lastModified();
      for (String folder : getPluginFolders()) {
        File folderFile = new File(folder);
        if (folderFile.exists() && folderFile.lastModified() > cacheModTime) {
          pluginCacheLoaded = true;
          return false;
        }
      }

      // Read number of entries
      int numEntries = in.readInt();

      // Read all entries
      for (int i = 0; i < numEntries; i++) {
        String path = in.readUTF();
        long modTime = in.readLong();
        int indexSize = in.readInt();
        byte[] indexBytes = new byte[indexSize];
        in.readFully(indexBytes);

        File jarFile = new File(path);

        // Parse the index
        Index index = new IndexReader(new ByteArrayInputStream(indexBytes)).read();

        pluginFiles.add(jarFile);
        indexCache.put(path, index);
      }

      pluginCacheLoaded = true;
      return true;

    } catch (Exception e) {
      LogChannel.GENERAL.logDebug("Failed to load plugin cache: " + e.getMessage());
      pluginFiles.clear();
      return false;
    }
  }

  private void scanPluginJars() throws HopFileException {
    for (String folder : getPluginFolders()) {
      for (File jarFile : findJarFiles(new File(folder))) {
        Index index = loadIndexFromJar(jarFile);
        if (index != null) {
          pluginFiles.add(jarFile);
          indexCache.put(jarFile.getAbsolutePath(), index);
        }
      }
    }
  }

  private void savePluginCache() {
    ensureCacheDir();
    File cacheFile = new File(getCacheBaseDir(), PLUGIN_CACHE_FILE);

    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)))) {

      // Write version
      out.writeInt(CACHE_VERSION);

      // Write folders hash
      out.writeInt(getPluginFolders().hashCode());

      // Write number of entries
      out.writeInt(pluginFiles.size());

      // Write each entry with its index
      for (File jarFile : pluginFiles) {
        String path = jarFile.getAbsolutePath();
        Index index = indexCache.get(path);

        out.writeUTF(path);
        out.writeLong(jarFile.lastModified());

        // Serialize index to bytes
        ByteArrayOutputStream indexBytes = new ByteArrayOutputStream();
        new IndexWriter(indexBytes).write(index);
        byte[] bytes = indexBytes.toByteArray();

        out.writeInt(bytes.length);
        out.write(bytes);
      }

    } catch (Exception e) {
      LogChannel.GENERAL.logDebug("Failed to save plugin cache: " + e.getMessage());
    }
  }

  // ==================== Index Access ====================

  public Index getIndex(File jarFile) throws HopFileException {
    String key = jarFile.getAbsolutePath();
    Index index = indexCache.get(key);

    if (index == null) {
      // Load from JAR if not in cache
      index = loadIndexFromJar(jarFile);
      if (index != null) {
        indexCache.put(key, index);
      }
    }

    return index;
  }

  private Index loadIndexFromJar(File jarFile) throws HopFileException {
    try (JarFile jar = new JarFile(jarFile)) {
      ZipEntry entry = jar.getEntry(ANNOTATION_INDEX_LOCATION);
      if (entry != null) {
        try (InputStream stream = jar.getInputStream(entry)) {
          return new IndexReader(stream).read();
        }
      }
    } catch (IOException e) {
      throw new HopFileException(
          MessageFormat.format("Error reading index from ''{0}''", jarFile), e);
    }
    return null;
  }

  // ==================== JAR File Discovery ====================

  public Set<File> findJarFiles(File folder) throws HopFileException {
    Set<File> files = jarFilesPerFolder.get(folder);
    if (files == null) {
      files = findFilesRecursive(folder);
      jarFilesPerFolder.put(folder, files);
    }
    return files;
  }

  private static Set<File> findFilesRecursive(File folder) {
    Set<File> files = new HashSet<>();
    File[] children = folder.listFiles();
    if (children != null) {
      for (File child : children) {
        if (child.isFile() && child.getName().endsWith(".jar")) {
          files.add(child);
        } else if (child.isDirectory() && !"lib".equals(child.getName())) {
          files.addAll(findFilesRecursive(child));
        }
      }
    }
    return files;
  }

  // ==================== Cache Management ====================

  public void clear() {
    nativeFiles.clear();
    pluginFiles.clear();
    indexCache.clear();
    jarFilesPerFolder.clear();
    nativeCacheLoaded = false;
    pluginCacheLoaded = false;
    cachedPluginFolders = null;
  }

  public boolean invalidatePersistentCache() {
    boolean deleted = true;
    File nativeCache = new File(getCacheBaseDir(), NATIVE_CACHE_FILE);
    if (nativeCache.exists()) {
      deleted &= nativeCache.delete();
    }
    File pluginCache = new File(getCacheBaseDir(), PLUGIN_CACHE_FILE);
    if (pluginCache.exists()) {
      deleted &= pluginCache.delete();
    }
    return deleted;
  }
}
