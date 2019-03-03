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
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.directories.Directories;
import org.apache.iotdb.db.monitor.StatMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileNodeManager2 {

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
}
