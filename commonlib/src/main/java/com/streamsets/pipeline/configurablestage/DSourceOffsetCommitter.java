/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.configurablestage;

import com.streamsets.pipeline.api.ClusterSource;
import com.streamsets.pipeline.api.OffsetCommitter;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.impl.Utils;

import java.util.List;
import java.util.Map;

public abstract class DSourceOffsetCommitter extends DSource implements OffsetCommitter {
  protected OffsetCommitter offsetCommitter;
  protected Source source;

  @Override
  Stage<Source.Context> createStage() {
    source = (Source) super.createStage();
    if (!(source instanceof OffsetCommitter)) {
      throw new RuntimeException(Utils.format("Stage '{}' does not implement '{}'", source.getClass().getName(),
                                              OffsetCommitter.class.getName()));
    }
    offsetCommitter = (OffsetCommitter) source;
    return source;
  }

  @Override
  public final void commit(String offset) throws StageException {
    offsetCommitter.commit(offset);
  }
}
