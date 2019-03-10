///**
// * Licensed to the Apache Software Foundation (ASF) under one
// * or more contributor license agreements.  See the NOTICE file
// * distributed with this work for additional information
// * regarding copyright ownership.  The ASF licenses this file
// * to you under the Apache License, Version 2.0 (the
// * "License"); you may not use this file except in compliance
// * with the License.  You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing,
// * software distributed under the License is distributed on an
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// * KIND, either express or implied.  See the License for the
// * specific language governing permissions and limitations
// * under the License.
// */
//
//package org.apache.iotdb.db.engine.filenode;
//
//import static java.time.ZonedDateTime.ofInstant;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.FileSystems;
//import java.nio.file.Files;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.ZonedDateTime;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Objects;
//import java.util.Set;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.concurrent.locks.ReadWriteLock;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;
//import org.apache.iotdb.db.conf.IoTDBConfig;
//import org.apache.iotdb.db.conf.IoTDBConstant;
//import org.apache.iotdb.db.conf.IoTDBDescriptor;
//import org.apache.iotdb.db.conf.directories.Directories;
//import org.apache.iotdb.db.engine.Processor;
//import org.apache.iotdb.db.engine.bufferwrite.Action;
//import org.apache.iotdb.db.engine.bufferwrite.ActionException;
//import org.apache.iotdb.db.engine.bufferwrite.BufferWriteProcessor;
//import org.apache.iotdb.db.engine.bufferwrite.FileNodeConstants;
//import org.apache.iotdb.db.engine.modification.Deletion;
//import org.apache.iotdb.db.engine.modification.Modification;
//import org.apache.iotdb.db.engine.modification.ModificationFile;
//import org.apache.iotdb.db.engine.overflow.io.OverflowProcessor;
//import org.apache.iotdb.db.engine.pool.MergeManager;
//import org.apache.iotdb.db.engine.querycontext.GlobalSortedSeriesDataSource;
//import org.apache.iotdb.db.engine.querycontext.OverflowSeriesDataSource;
//import org.apache.iotdb.db.engine.querycontext.QueryDataSource;
//import org.apache.iotdb.db.engine.querycontext.ReadOnlyMemChunk;
//import org.apache.iotdb.db.engine.querycontext.UnsealedTsFile;
//import org.apache.iotdb.db.engine.version.SimpleFileVersionController;
//import org.apache.iotdb.db.engine.version.VersionController;
//import org.apache.iotdb.db.exception.BufferWriteProcessorException;
//import org.apache.iotdb.db.exception.ErrorDebugException;
//import org.apache.iotdb.db.exception.FileNodeProcessorException;
//import org.apache.iotdb.db.exception.OverflowProcessorException;
//import org.apache.iotdb.db.exception.PathErrorException;
//import org.apache.iotdb.db.exception.ProcessorException;
//import org.apache.iotdb.db.metadata.MManager;
//import org.apache.iotdb.db.monitor.IStatistic;
//import org.apache.iotdb.db.monitor.MonitorConstants;
//import org.apache.iotdb.db.monitor.StatMonitor;
//import org.apache.iotdb.db.query.context.QueryContext;
//import org.apache.iotdb.db.query.factory.SeriesReaderFactory;
//import org.apache.iotdb.db.query.reader.IReader;
//import org.apache.iotdb.db.utils.MemUtils;
//import org.apache.iotdb.db.utils.QueryUtils;
//import org.apache.iotdb.db.utils.TimeValuePair;
//import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
//import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
//import org.apache.iotdb.tsfile.file.footer.ChunkGroupFooter;
//import org.apache.iotdb.tsfile.file.metadata.ChunkMetaData;
//import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
//import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
//import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
//import org.apache.iotdb.tsfile.read.common.Path;
//import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
//import org.apache.iotdb.tsfile.read.filter.TimeFilter;
//import org.apache.iotdb.tsfile.read.filter.basic.Filter;
//import org.apache.iotdb.tsfile.read.filter.factory.FilterFactory;
//import org.apache.iotdb.tsfile.utils.Pair;
//import org.apache.iotdb.tsfile.write.chunk.ChunkBuffer;
//import org.apache.iotdb.tsfile.write.chunk.ChunkWriterImpl;
//import org.apache.iotdb.tsfile.write.record.TSRecord;
//import org.apache.iotdb.tsfile.write.record.datapoint.LongDataPoint;
//import org.apache.iotdb.tsfile.write.schema.FileSchema;
//import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
//import org.apache.iotdb.tsfile.write.writer.TsFileIOWriter;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class FileNodeProcessor2 extends Processor implements IStatistic {
//
//  private static final String WARN_NO_SUCH_OVERFLOWED_FILE = "Can not find any tsfile which"
//      + " will be overflowed in the filenode processor {}, ";
//  private static final String RESTORE_FILE_SUFFIX = ".restore";
//  private static final Logger LOGGER = LoggerFactory.getLogger(FileNodeProcessor2.class);
//  private static final IoTDBConfig TsFileDBConf = IoTDBDescriptor.getInstance().getConfig();
//  private static final MManager mManager = MManager.getInstance();
//  private static final Directories directories = Directories.getInstance();
//
//
//  /**
//   * constructor of FileNodeProcessor.
//   */
//  FileNodeProcessor2(String fileNodeDirPath, String processorName)
//      throws FileNodeProcessorException {
//    super(processorName);
//
//  }
//
//  @Override
//  public Map<String, AtomicLong> getStatParamsHashMap() {
//    return null;
//  }
//
//  @Override
//  public void registStatMetadata() {
//  }
//
//  @Override
//  public List<String> getAllPathForStatistic() {
//
//  }
//
//  @Override
//  public Map<String, TSRecord> getAllStatisticsValue() {
//
//  }
//
//
//
//  public boolean isOverflowed() {
//
//  }
//
//  /**
//   * if overflow insert, update and delete write into this filenode processor, set
//   * <code>isOverflowed</code> to true.
//   */
//  public void setOverflowed(boolean isOverflowed) {
//
//  }
//
//  public FileNodeProcessorStatus getFileNodeProcessorStatus() {
//
//  }
//
//
//
//  /**
//   * get buffer write processor by processor name and insert time.
//   */
//  public BufferWriteProcessor getBufferWriteProcessor(String processorName, long insertTime)
//      throws FileNodeProcessorException {
//
//  }
//
//  /**
//   * get buffer write processor.
//   */
//  public BufferWriteProcessor getBufferWriteProcessor() throws FileNodeProcessorException {
//
//  }
//
//  /**
//   * get overflow processor by processor name.
//   */
//  public OverflowProcessor getOverflowProcessor(String processorName) throws IOException {
//
//  }
//
//  /**
//   * get overflow processor.
//   */
//  public OverflowProcessor getOverflowProcessor() {
//
//  }
//
//  boolean hasOverflowProcessor() {
//
//  }
//
//  public void setBufferwriteProcessroToClosed() {
//
//  }
//
//  public boolean hasBufferwriteProcessor() {
//
//  }
//
//
//
//  /**
//   * add multiple pass lock.
//   */
//  public int addMultiPassLock() {
//
//  }
//
//  /**
//   * remove multiple pass lock. TODO: use the return value or remove it.
//   */
//  public boolean removeMultiPassLock(int token) {
//
//  }
//
//  /**
//   * query data.
//   */
//  public <T extends Comparable<T>> QueryDataSource query(String deviceId, String measurementId,
//      QueryContext context) throws FileNodeProcessorException {
//
//  }
//
//
//
//  /**
//   * get overlap tsfiles which are conflict with the appendFile.
//   *
//   * @param appendFile the appended tsfile information
//   */
//  public List<String> getOverlapFiles(IntervalFileNode appendFile, String uuid)
//      throws FileNodeProcessorException {
//
//  }
//
//  private void getOverlapFiles(IntervalFileNode appendFile, IntervalFileNode intervalFileNode,
//      String uuid, List<String> overlapFiles) throws IOException {
//
//  }
//
//  /**
//   * add time series.
//   */
//  public void addTimeSeries(String measurementId, TSDataType dataType, TSEncoding encoding,
//      CompressionType compressor, Map<String, String> props) {
//
//  }
//
//
//
//  /**
//   * Merge this storage group, merge the tsfile data with overflow data.
//   */
//  public void merge() throws FileNodeProcessorException {
//
//  }
//
//
//
//  @Override
//  public boolean canBeClosed() {
//
//  }
//
//  @Override
//  public FileNodeFlushFuture flush() throws IOException {
//
//  }
//
//
//
//  /**
//   * Close the overflow processor.
//   */
//  public void closeOverflow() throws FileNodeProcessorException {
//
//  }
//
//
//
//  @Override
//  public void close() throws FileNodeProcessorException {
//
//  }
//
//  /**
//   * deregister the filenode processor.
//   */
//  public void delete() throws ProcessorException {
//
//  }
//
//  @Override
//  public long memoryUsage() {
//
//  }
//
//
//  /**
//   * Delete data whose timestamp <= 'timestamp' and belong to timeseries deviceId.measurementId.
//   *
//   * @param deviceId the deviceId of the timeseries to be deleted.
//   * @param measurementId the measurementId of the timeseries to be deleted.
//   * @param timestamp the delete range is (0, timestamp].
//   */
//  public void delete(String deviceId, String measurementId, long timestamp) throws IOException {
//
//  }
//
//
//
//  /**
//   * Similar to delete(), but only deletes data in BufferWrite. Only used by WAL recovery.
//   */
//  public void deleteBufferWrite(String deviceId, String measurementId, long timestamp)
//      throws IOException {
//
//  }
//
//  /**
//   * Similar to delete(), but only deletes data in Overflow. Only used by WAL recovery.
//   */
//  public void deleteOverflow(String deviceId, String measurementId, long timestamp)
//      throws IOException {
//
//  }
//
//
//
//
//}