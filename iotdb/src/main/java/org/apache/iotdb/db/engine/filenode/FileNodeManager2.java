/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.engine.filenode;


import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.directories.Directories;
import org.apache.iotdb.db.engine.memcontrol.BasicMemController;
import org.apache.iotdb.db.engine.querycontext.QueryDataSource;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.FileNodeProcessorException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.monitor.IStatistic;
import org.apache.iotdb.db.monitor.StatMonitor;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.service.IService;
import org.apache.iotdb.db.service.ServiceType;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileNodeManager2  implements IStatistic, IService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileNodeManager.class);
  private static final IoTDBConfig TsFileDBConf = IoTDBDescriptor.getInstance().getConfig();
  private static final Directories directories = Directories.getInstance();

  /**
   * a folder that persist FileNodeProcessorStore classes. Each stroage group will have a subfolder.
   * by default, it is data/system/info
   */
  private final String baseDir;

  public static FileNodeManager2 getInstance() {
    return FileNodeManagerHolder.INSTANCE;
  }

  private static class FileNodeManagerHolder {

    private FileNodeManagerHolder() {
    }

    private static final FileNodeManager2 INSTANCE = new FileNodeManager2(IoTDBDescriptor.getInstance().getConfig().fileNodeDir);
  }

  private FileNodeManager2(String baseDir) {
    //create folder first
    File dir = new File(baseDir);
    if (dir.mkdirs()) {
      LOGGER.info("{} dir's home folder doesn't exist, create it", dir.getPath());
    }
    this.baseDir = dir.getAbsolutePath();


  }
  private void registToStatMonitor() {
    StatMonitor.getInstance().recovery();
  }

  /**
   * recovery the filenode manager.
   */
  public void recovery() {
    List<String> filenodeNames = null;
    try {
      filenodeNames = MManager.getInstance().getAllFileNames();
    } catch (PathErrorException e) {
      LOGGER.error("Restoring all FileNodes failed.", e);
      return;
    }
    for (String filenodeName : filenodeNames) {
      FileNodeProcessor fileNodeProcessor = null;
      try {
        fileNodeProcessor = getProcessor(filenodeName, true);
        if (fileNodeProcessor.shouldRecovery()) {
          LOGGER.info("Recovery the filenode processor, the filenode is {}, the status is {}",
              filenodeName, fileNodeProcessor.getFileNodeProcessorStatus());
          fileNodeProcessor.fileNodeRecovery();
        }
      } catch (FileNodeManagerException | FileNodeProcessorException e) {
        LOGGER.error("Restoring fileNode {} failed.", filenodeName, e);
      } finally {
        if (fileNodeProcessor != null) {
          fileNodeProcessor.writeUnlock();
        }
      }
      // add index check sum
    }
  }

  /**
   * insert TsRecord into storage group.
   *
   * @param tsRecord input Data
   * @param isMonitor if true, the insertion is done by StatMonitor and the statistic Info will not
   * be recorded. if false, the statParamsHashMap will be updated.
   * @return an int value represents the insert type
   */
  public int insert(TSRecord tsRecord, boolean isMonitor) throws FileNodeManagerException {

  }

  /**
   * update data.
   */
  public void update(String deviceId, String measurementId, long startTime, long endTime,
      TSDataType type, String v)
      throws FileNodeManagerException {

  }

  /**
   * delete data.
   */
  public void delete(String deviceId, String measurementId, long timestamp)
      throws FileNodeManagerException {

  }

  /**
   * Similar to delete(), but only deletes data in BufferWrite. Only used by WAL recovery.
   */
  public void deleteBufferWrite(String deviceId, String measurementId, long timestamp)
      throws FileNodeManagerException {

  }

  /**
   * Similar to delete(), but only deletes data in Overflow. Only used by WAL recovery.
   */
  public void deleteOverflow(String deviceId, String measurementId, long timestamp)
      throws FileNodeManagerException {

  }

  /**
   * begin query.
   * @param  deviceId queried deviceId
   * @return a query token for the device.
   */
  public int beginQuery(String deviceId) throws FileNodeManagerException {

  }

  /**
   * query data.
   */
  public QueryDataSource query(SingleSeriesExpression seriesExpression, QueryContext context)
      throws FileNodeManagerException {

  }

  /**
   * end query.
   */
  public void endQuery(String deviceId, int token) throws FileNodeManagerException {

  }

  /**
   * Append one specified tsfile to the storage group. <b>This method is only provided for
   * transmission module</b>
   *
   * @param fileNodeName the seriesPath of storage group
   * @param appendFile the appended tsfile information
   */
  public boolean appendFileToFileNode(String fileNodeName, IntervalFileNode appendFile,
      String appendFilePath) throws FileNodeManagerException {
    // 0. if the filenode is in merging process, block.
    // 1. if the appendFile has a longer history (i.e, startTime and endTime of each Devices are
    // suitable), then just copy the file to the data folder and update file lists;
    // 2. if the appendFile has a longer history but the startTime and endTime of some devices are
    // overlappted with other TsFiles, then forward this part of data into overflowProcessor and keep
    // the rest part of data as a new TsFile.
    // 3. if the start time of some devices >= bufferwrite's timestamp, then flush the bufferwrite,
    // and then copy the file to the dta folder
  }

  /**
   * delete one filenode.
   */
  public void deleteOneFileNode(String processorName) throws FileNodeManagerException {

  }

  /**
   * add time series.
   */
  public void addTimeSeries(
      Path path, TSDataType dataType, TSEncoding encoding, CompressionType compressor,
      Map<String, String> props) throws FileNodeManagerException {

  }

  /**
   * Force to close the filenode processor.
   */
  public void closeOneFileNode(String processorName) throws FileNodeManagerException {

  }

  /**
   * delete all filenode.
   */
  public synchronized boolean deleteAll() throws FileNodeManagerException {

  }

  /**
   * Try to close All.
   */
  public void closeAll() throws FileNodeManagerException {

  }

  /**
   * force flush to control memory usage.
   */
  public void forceFlush(BasicMemController.UsageLevel level) {

  }

  /**
   * recover filenode.
   */
  public void recoverFileNode(String filenodeName)
      throws FileNodeManagerException {

  }

  /**
   * merge all overflowed filenode.
   *
   * @throws FileNodeManagerException FileNodeManagerException
   */
  public void mergeAll() throws FileNodeManagerException {

  }





  @Override
  public Map<String, TSRecord> getAllStatisticsValue() {
    return null;
  }

  @Override
  public void registStatMetadata() {

  }

  @Override
  public List<String> getAllPathForStatistic() {
    return null;
  }

  @Override
  public Map<String, AtomicLong> getStatParamsHashMap() {
    return null;
  }

  @Override
  public void start() throws StartupException {

  }

  @Override
  public void stop() {

  }

  @Override
  public ServiceType getID() {
    return null;
  }
}
