/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.cluster;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.PipelineConfiguration;
import com.streamsets.pipeline.main.RuntimeInfo;
import com.streamsets.pipeline.stagelibrary.StageLibraryTask;
import com.streamsets.pipeline.util.SystemProcessFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class SparkManager {
  private static final Logger LOG = LoggerFactory.getLogger(SparkManager.class);
  private final ListeningExecutorService executorService = MoreExecutors.
    listeningDecorator(Executors.newFixedThreadPool(3));
  private final SystemProcessFactory systemProcessFactory;
  private final SparkProvider sparkProvider;
  private final File tempDir;
  private final File sparkManager;
  private URLClassLoader apiCL;
  private URLClassLoader containerCL;

  public SparkManager(RuntimeInfo runtimeInfo, File tempDir) {
    this(new SystemProcessFactory(), new SparkProviderImpl(runtimeInfo), tempDir,
      new File(runtimeInfo.getLibexecDir(), "spark-manager"), null, null);
  }

  @VisibleForTesting
  public SparkManager(SystemProcessFactory systemProcessFactory, SparkProvider sparkProvider,
                      File tempDir, File sparkManager, URLClassLoader apiCL,
                      URLClassLoader containerCL) {
    this.systemProcessFactory = systemProcessFactory;
    this.sparkProvider = sparkProvider;
    this.tempDir = tempDir;
    this.sparkManager = sparkManager;
    if (containerCL == null) {
      this.containerCL = (URLClassLoader)getClass().getClassLoader();
    } else {
      this.containerCL = containerCL;
    }
    if (apiCL == null) {
      this.apiCL = (URLClassLoader)this.containerCL.getParent();
    } else {
      this.apiCL = apiCL;
    }
    Utils.checkState(tempDir.isDirectory(), errorString("Temp directory does not exist: {}", tempDir));
    Utils.checkState(sparkManager.isFile(), errorString("spark-manager does not exist: {}", sparkManager));
    Utils.checkState(sparkManager.canExecute(), errorString("spark-manager is not executable: {}", sparkManager));
  }

  public ListenableFuture<ApplicationState> submit(final PipelineConfiguration pipelineConfiguration,
                                         final StageLibraryTask stageLibrary,
                                         final File etcDir, final File resourcesDir, final File staticWebDir,
                                         final File bootstrapDir, final Map<String, String> environment,
                                         final Map<String, String> sourceInfo, final long timeout) {
    return executorService.submit(new Callable<ApplicationState>() {
      @Override
      public ApplicationState call() throws Exception {
        return sparkProvider.startPipeline(systemProcessFactory, sparkManager, tempDir, environment, sourceInfo,
          pipelineConfiguration, stageLibrary, etcDir, resourcesDir, staticWebDir, bootstrapDir, apiCL, containerCL,
          timeout);
      }
    });
  }

  public ListenableFuture<Void> kill(final ApplicationState applicationState,
                                     final PipelineConfiguration pipelineConfiguration) {
    return executorService.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        sparkProvider.killPipeline(systemProcessFactory, sparkManager, tempDir, applicationState.getId(),
          pipelineConfiguration);
        return null;
      }
    });
  }

  public ListenableFuture<ClusterPipelineStatus> getStatus(final ApplicationState applicationState,
                                             final PipelineConfiguration pipelineConfiguration) {
    return executorService.submit(new Callable<ClusterPipelineStatus>() {
      @Override
      public ClusterPipelineStatus call() throws Exception {
        return sparkProvider.getStatus(systemProcessFactory, sparkManager, tempDir, applicationState.getId(),
          pipelineConfiguration);
      }
    });
  }

  private static String errorString(String template, Object... args) {
    return Utils.format("ERROR: " + template, args);
  }
}
