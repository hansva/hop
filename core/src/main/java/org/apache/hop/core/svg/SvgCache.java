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

package org.apache.hop.core.svg;

import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.util.SVGConstants;
import org.apache.batik.util.XMLResourceDescriptor;
import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

/**
 * Cache for SVG files. Maintains both an in-memory cache of parsed SVG documents and a persistent
 * disk cache of SVG dimensions to avoid expensive GVTBuilder calculations on startup.
 */
public class SvgCache {
  private static final String DIMENSION_CACHE_FILE = "svg-dimension-cache.index";
  private static final String CACHE_VERSION = "1";

  private static SvgCache instance;

  private Map<String, SvgCacheEntry> fileDocumentMap;

  // Persistent dimension cache: filename -> "width,height,x,y"
  private Map<String, int[]> dimensionCache;
  private boolean dimensionCacheDirty = false;
  private boolean dimensionCacheLoaded = false;

  private SvgCache() {
    fileDocumentMap = new HashMap<>();
    dimensionCache = new ConcurrentHashMap<>();
  }

  /**
   * Gets instance
   *
   * @return value of instance
   */
  public static SvgCache getInstance() {
    if (instance == null) {
      instance = new SvgCache();
    }
    return instance;
  }

  /** Get the dimension cache file location */
  private File getDimensionCacheFile() {
    File cacheDir = new File(Const.HOP_AUDIT_FOLDER, "caches");
    return new File(cacheDir, DIMENSION_CACHE_FILE);
  }

  /** Load dimension cache from disk */
  private synchronized void loadDimensionCache() {
    if (dimensionCacheLoaded) {
      return;
    }
    dimensionCacheLoaded = true;

    File cacheFile = getDimensionCacheFile();
    if (!cacheFile.exists()) {
      return;
    }

    try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
      String version = reader.readLine();
      if (!CACHE_VERSION.equals(version)) {
        // Version mismatch - ignore cache
        return;
      }

      String line;
      while ((line = reader.readLine()) != null) {
        int tabIndex = line.indexOf('\t');
        if (tabIndex > 0) {
          String filename = line.substring(0, tabIndex);
          String[] parts = line.substring(tabIndex + 1).split(",");
          if (parts.length == 4) {
            int[] dims = new int[4];
            dims[0] = Integer.parseInt(parts[0]); // width
            dims[1] = Integer.parseInt(parts[1]); // height
            dims[2] = Integer.parseInt(parts[2]); // x
            dims[3] = Integer.parseInt(parts[3]); // y
            dimensionCache.put(filename, dims);
          }
        }
      }
    } catch (Exception e) {
      // Silently ignore - cache will be rebuilt
    }
  }

  /** Save dimension cache to disk */
  public synchronized void saveDimensionCache() {
    if (!dimensionCacheDirty || dimensionCache.isEmpty()) {
      return;
    }

    File cacheFile = getDimensionCacheFile();
    try {
      File parentDir = cacheFile.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs();
      }

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(cacheFile))) {
        writer.write(CACHE_VERSION);
        writer.newLine();

        for (Map.Entry<String, int[]> entry : dimensionCache.entrySet()) {
          int[] dims = entry.getValue();
          writer.write(
              entry.getKey() + "\t" + dims[0] + "," + dims[1] + "," + dims[2] + "," + dims[3]);
          writer.newLine();
        }
      }
      dimensionCacheDirty = false;
    } catch (Exception e) {
      // Silently ignore save errors
    }
  }

  /** Get cached dimensions for a file, or null if not cached */
  private int[] getCachedDimensions(String filename) {
    loadDimensionCache();
    return dimensionCache.get(filename);
  }

  /** Cache dimensions for a file */
  private void cacheDimensions(String filename, int width, int height, int x, int y) {
    dimensionCache.put(filename, new int[] {width, height, x, y});
    dimensionCacheDirty = true;
  }

  public static synchronized SvgCacheEntry findSvg(String filename) {
    return getInstance().fileDocumentMap.get(filename);
  }

  public static synchronized SvgCacheEntry loadSvg(SvgFile svgFile) throws HopException {

    SvgCacheEntry cacheEntry = findSvg(svgFile.getFilename());
    if (cacheEntry != null) {
      return cacheEntry;
    }

    try {
      String parser = XMLResourceDescriptor.getXMLParserClassName();
      SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
      InputStream svgStream = svgFile.getClassLoader().getResourceAsStream(svgFile.getFilename());
      if (svgStream == null) {
        // Retry on the regular filesystem using standard Java I/O (faster than VFS)
        File file = new File(svgFile.getFilename());
        if (file.exists()) {
          svgStream = new FileInputStream(file);
        }
      }
      SVGDocument svgDocument = factory.createSVGDocument(svgFile.getFilename(), svgStream);
      SVGSVGElement elSVG = svgDocument.getRootElement();

      float width = -1;
      float height = -1;
      float x = 0;
      float y = 0;

      // First check if we have cached dimensions (avoids expensive GVTBuilder)
      int[] cachedDims = getInstance().getCachedDimensions(svgFile.getFilename());
      if (cachedDims != null) {
        width = cachedDims[0];
        height = cachedDims[1];
        x = cachedDims[2];
        y = cachedDims[3];
      } else {
        // See if the element has a "width" and a "height" attribute...
        String widthAttribute = elSVG.getAttribute("width");
        String heightAttribute = elSVG.getAttribute("height");
        if (widthAttribute != null && heightAttribute != null) {
          width = (float) Const.toDouble(widthAttribute.replace("px", "").replace("mm", ""), -1.0d);
          height =
              (float) Const.toDouble(heightAttribute.replace("px", "").replace("mm", ""), -1.0d);
        }
        String xAttribute = elSVG.getAttribute("x");
        String yAttribute = elSVG.getAttribute("y");
        if (xAttribute != null && yAttribute != null) {
          x = (float) Const.toDouble(xAttribute.replace("px", "").replace("mm", ""), 0d);
          y = (float) Const.toDouble(yAttribute.replace("px", "").replace("mm", ""), 0d);
        }

        // If we don't have width and height we'll have to calculate it...
        if (width <= 1 || height <= 1) {
          // Figure out the primitives bounds...
          UserAgent agent = new UserAgentAdapter();
          DocumentLoader loader = new DocumentLoader(agent);
          BridgeContext context = new BridgeContext(agent, loader);
          context.setDynamic(true);
          GVTBuilder builder = new GVTBuilder();
          GraphicsNode root = builder.build(context, svgDocument);

          // We need to go through the document to figure it out, unfortunately.
          // It is slower but should always work.
          Rectangle2D primitiveBounds = root.getPrimitiveBounds();

          width = (float) primitiveBounds.getWidth();
          height = (float) primitiveBounds.getHeight();
          x = (float) primitiveBounds.getX();
          y = (float) primitiveBounds.getY();

          if (width <= 1 || height <= 1) {
            // See if we can use a viewbox...
            String attributeNS = elSVG.getAttributeNS(null, SVGConstants.SVG_VIEW_BOX_ATTRIBUTE);
            if (StringUtils.isNotEmpty(attributeNS)) {
              String[] parts = attributeNS.split(" ");
              if (parts.length == 4) {
                // Usually this is in the form "0 0 100 200" : x y width height
                width = (float) Const.toDouble(parts[2], 0.0);
                height = (float) Const.toDouble(parts[3], 0.0);
              }
            }
          }
        }

        // Cache the calculated dimensions for next startup
        if (width > 1 && height > 1) {
          getInstance()
              .cacheDimensions(
                  svgFile.getFilename(),
                  Math.round(width),
                  Math.round(height),
                  Math.round(x),
                  Math.round(y));
        }
      }

      if (width <= 1 || height <= 1) {
        throw new HopException(
            "Couldn't determine width or height of file : " + svgFile.getFilename());
      }

      cacheEntry =
          new SvgCacheEntry(
              svgFile.getFilename(),
              svgDocument,
              Math.round(width),
              Math.round(height),
              Math.round(x),
              Math.round(y));
      getInstance().fileDocumentMap.put(svgFile.getFilename(), cacheEntry);
      return cacheEntry;
    } catch (Exception e) {
      throw new HopException("Error loading SVG file " + svgFile.getFilename(), e);
    }
  }

  public static synchronized void addSvg(
      String filename, SVGDocument svgDocument, int width, int height, int x, int y) {
    getInstance()
        .fileDocumentMap
        .put(filename, new SvgCacheEntry(filename, svgDocument, width, height, x, y));
    // Also cache the dimensions
    getInstance().cacheDimensions(filename, width, height, x, y);
  }

  public void clear() {
    fileDocumentMap.clear();
  }
}
