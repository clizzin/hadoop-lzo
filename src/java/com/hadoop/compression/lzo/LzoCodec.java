/*
 * This file is part of Hadoop-Gpl-Compression.
 *
 * Hadoop-Gpl-Compression is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Hadoop-Gpl-Compression is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hadoop-Gpl-Compression.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.hadoop.compression.lzo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.BlockCompressorStream;
import org.apache.hadoop.io.compress.BlockDecompressorStream;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.Compressor;
import org.apache.hadoop.io.compress.Decompressor;

/**
 * A {@link org.apache.hadoop.io.compress.CompressionCodec} for a streaming
 * <b>lzo</b> compression/decompression pair.
 * http://www.oberhumer.com/opensource/lzo/
 *
 */
public class LzoCodec implements Configurable, CompressionCodec {
  private static final Log LOG = LogFactory.getLog(LzoCodec.class.getName());

  public static final String LZO_COMPRESSOR_KEY = "io.compression.codec.lzo.compressor";
  public static final String LZO_DECOMPRESSOR_KEY = "io.compression.codec.lzo.decompressor";
  public static final String LZO_BUFFER_SIZE_KEY = "io.compression.codec.lzo.buffersize";
  public static final int DEFAULT_LZO_BUFFER_SIZE = 256 * 1024;
  public static final int MAX_BLOCK_SIZE = 64*1024*1024;


  private Configuration conf;

  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  public Configuration getConf() {
    return conf;
  }

  private static boolean nativeLzoLoaded = false;

  static {
    if (GPLNativeCodeLoader.isNativeCodeLoaded()) {
      nativeLzoLoaded = LzoCompressor.isNativeLzoLoaded() &&
      LzoDecompressor.isNativeLzoLoaded();

      if (nativeLzoLoaded) {
        LOG.info("Successfully loaded & initialized native-lzo library [hadoop-lzo rev " + getRevisionHash() + "]");
      } else {
        LOG.error("Failed to load/initialize native-lzo library");
      }
    } else {
      LOG.error("Cannot load native-lzo without native-hadoop");
    }
  }

  /**
   * Check if native-lzo library is loaded & initialized.
   *
   * @param conf configuration
   * @return <code>true</code> if native-lzo library is loaded & initialized;
   *         else <code>false</code>
   */
  public static boolean isNativeLzoLoaded(Configuration conf) {
    return nativeLzoLoaded && conf.getBoolean("hadoop.native.lib", true);
  }

  public static String getRevisionHash() {
    try {
      Properties p = new Properties();
      p.load(LzoCodec.class.getResourceAsStream("/build.properties"));
      return p.getProperty("build_revision");
    } catch (IOException e) {
      LOG.error("Could not find build properties file with revision hash");
      return "UNKNOWN";
    }
  }

  public CompressionOutputStream createOutputStream(OutputStream out) throws IOException {
    return createOutputStream(out, createCompressor());
  }

  public CompressionOutputStream createOutputStream(OutputStream out,
      Compressor compressor) throws IOException {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }

    /**
     * <b>http://www.oberhumer.com/opensource/lzo/lzofaq.php</b>
     *
     * How much can my data expand during compression ?
     * ================================================
     * LZO will expand incompressible data by a little amount.
     * I still haven't computed the exact values, but I suggest using
     * these formulas for a worst-case expansion calculation:
     *
     * Algorithm LZO1, LZO1A, LZO1B, LZO1C, LZO1F, LZO1X, LZO1Y, LZO1Z:
     * ----------------------------------------------------------------
     * output_block_size = input_block_size + (input_block_size / 16) + 64 + 3
     *
     * This is about 106% for a large block size.
     *
     * Algorithm LZO2A:
     * ----------------
     * output_block_size = input_block_size + (input_block_size / 8) + 128 + 3
     */

    // Create the lzo output-stream
    LzoCompressor.CompressionStrategy strategy = getCompressionStrategy(conf);
    int bufferSize = getBufferSize(conf);
    int compressionOverhead = strategy.name().contains("LZO1") ?
        (bufferSize >> 4) + 64 + 3 : (bufferSize >> 3) + 128 + 3;

    return new BlockCompressorStream(out, compressor, bufferSize,
        compressionOverhead);
  }

  public Class<? extends Compressor> getCompressorType() {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }
    return LzoCompressor.class;
  }

  public Compressor createCompressor() {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }

    return new LzoCompressor(conf);
  }

  public CompressionInputStream createInputStream(InputStream in)
  throws IOException {
    return createInputStream(in, createDecompressor());
  }

  public CompressionInputStream createInputStream(InputStream in,
      Decompressor decompressor)
  throws IOException {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }
    return new BlockDecompressorStream(in, decompressor,
        conf.getInt(LZO_BUFFER_SIZE_KEY, DEFAULT_LZO_BUFFER_SIZE));
  }

  public Class<? extends Decompressor> getDecompressorType() {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }
    return LzoDecompressor.class;
  }

  public Decompressor createDecompressor() {
    // Ensure native-lzo library is loaded & initialized
    if (!isNativeLzoLoaded(conf)) {
      throw new RuntimeException("native-lzo library not available");
    }

    return new LzoDecompressor(
      getDecompressionStrategy(conf),
      getBufferSize(conf));
  }

  /**
   * Get the default filename extension for this kind of compression.
   * @return the extension including the '.'
   */
  public String getDefaultExtension() {
    return ".lzo_deflate";
  }

  static LzoCompressor.CompressionStrategy getCompressionStrategy(Configuration conf) {
    return LzoCompressor.CompressionStrategy.valueOf(
          conf.get(LZO_COMPRESSOR_KEY,
            LzoCompressor.CompressionStrategy.LZO1X_1.name()));
  }

  static LzoDecompressor.CompressionStrategy getDecompressionStrategy(Configuration conf) {
    return LzoDecompressor.CompressionStrategy.valueOf(
          conf.get(LZO_DECOMPRESSOR_KEY,
            LzoDecompressor.CompressionStrategy.LZO1X.name()));
  }

  static int getBufferSize(Configuration conf) {
    return conf.getInt(LZO_BUFFER_SIZE_KEY, DEFAULT_LZO_BUFFER_SIZE);
  }

  public static void setCompressionStrategy(Configuration conf,
                                            LzoCompressor.CompressionStrategy strategy) {
    conf.set(LZO_COMPRESSOR_KEY, strategy.name());
  }

  public static void setDecompressionStrategy(Configuration conf,
                                              LzoDecompressor.CompressionStrategy strategy) {
    conf.set(LZO_DECOMPRESSOR_KEY, strategy.name());
  }

  public static void setBufferSize(Configuration conf, int bufferSize) {
    conf.setInt(LZO_BUFFER_SIZE_KEY, bufferSize);
  }

}
