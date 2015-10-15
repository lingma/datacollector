/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.processor.expression;

import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.sdk.ProcessorRunner;
import com.streamsets.pipeline.sdk.RecordCreator;
import com.streamsets.pipeline.sdk.StageRunner;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestExpressionProcessor {

  @Test
  public void testInvalidExpression() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${(record:value('baseSalary') +record:value('bonus') * 2}"; //invalid expression string, missing ")"
    expressionProcessorConfig.fieldToSet = "/grossSalary";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addOutputLane("a").build();

    List<Stage.ConfigIssue> issues = runner.runValidateConfigs();
    Assert.assertEquals(1, issues.size());
    Assert.assertTrue(issues.get(0).toString().contains("EXPR_00"));
  }

  @Test
  public void tesExpressionEvaluationFailure() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${record:value('/baseSalary') + record:value('/bonusx')}";
    expressionProcessorConfig.fieldToSet = "/grossSalary";
    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)

      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addOutputLane("a").build();

    try {
      runner.runInit();

      Map<String, Field> map = new LinkedHashMap<>();
      map.put("baseSalary", Field.create(Field.Type.STRING, "100000.25"));
      map.put("bonusx", Field.create(Field.Type.STRING, "xxx"));
      map.put("tax", Field.create(Field.Type.STRING, "30000.25"));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      runner.runProcess(ImmutableList.of(record));
      Assert.fail("Stage exception expected as the expression string is not valid");
    } catch (OnRecordErrorException e) {
      Assert.assertEquals(Errors.EXPR_03, e.getErrorCode());
    }
  }

  @Test
  public void testReplaceExistingFieldInExpression() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${record:value('/baseSalary') + record:value('/bonus') - record:value('/tax')}";
    expressionProcessorConfig.fieldToSet = "/baseSalary";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("baseSalary", Field.create(Field.Type.DOUBLE, 100000.25));
      map.put("bonus", Field.create(Field.Type.INTEGER, 2000));
      map.put("tax", Field.create(Field.Type.DECIMAL, new BigDecimal(30000.25)));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(3, result.size());
      Assert.assertTrue(result.containsKey("baseSalary"));
      Assert.assertEquals(0, new BigDecimal(100000.25 + 2000 - 30000.25).compareTo((BigDecimal) result.get("baseSalary").getValue()));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testSimpleExpression() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${record:value('/baseSalary') + record:value('/bonus') - record:value('/tax')}";
    expressionProcessorConfig.fieldToSet = "/netSalary";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("baseSalary", Field.create(Field.Type.DOUBLE, 100000.25));
      map.put("bonus", Field.create(Field.Type.INTEGER, 2000));
      map.put("tax", Field.create(Field.Type.DECIMAL, new BigDecimal(30000.25)));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(4, result.size());
      Assert.assertTrue(result.containsKey("netSalary"));
      Assert.assertEquals(0, new BigDecimal(100000.25 + 2000 - 30000.25).compareTo((BigDecimal) result.get("netSalary").getValue()));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testListMapType() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfigMap = new ExpressionProcessorConfig();
    expressionProcessorConfigMap.expression = "${record:value('/mapField')}";
    expressionProcessorConfigMap.fieldToSet = "/mapFieldCopy";

    ExpressionProcessorConfig expressionProcessorConfigListMap = new ExpressionProcessorConfig();
    expressionProcessorConfigListMap.expression = "${record:value('/listMapField')}";
    expressionProcessorConfigListMap.fieldToSet = "/listMapFieldCopy";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs",
        ImmutableList.of(expressionProcessorConfigMap, expressionProcessorConfigListMap))
      .addOutputLane("a").build();
    runner.runInit();

    try {
      HashMap<String, Field> mapField = new HashMap<>();
      mapField.put("key1", Field.create("value1"));
      mapField.put("key2", Field.create("value2"));

      LinkedHashMap<String, Field> listMapField = new LinkedHashMap<>();
      listMapField.put("key1", Field.create("value1"));
      listMapField.put("key2", Field.create("value2"));

      Map<String, Field> map = new LinkedHashMap<>();
      map.put("mapField", Field.create(Field.Type.MAP, mapField));
      map.put("listMapField", Field.create(Field.Type.LIST_MAP, listMapField));

      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(4, result.size());
      Assert.assertTrue(result.containsKey("mapField"));
      Assert.assertTrue(result.containsKey("listMapField"));
      Assert.assertTrue(result.containsKey("mapFieldCopy"));
      Assert.assertTrue(result.containsKey("listMapFieldCopy"));
      Assert.assertEquals(Field.Type.MAP, result.get("mapField").getType());
      Assert.assertEquals(Field.Type.LIST_MAP, result.get("listMapFieldCopy").getType());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testExpressionWithConstants() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${record:value('/baseSalary') + BONUS - record:value('/tax')}";
    expressionProcessorConfig.fieldToSet = "/netSalary";

    Map<String, Object> constants = new HashMap<>();
    constants.put("BONUS", 2000);
    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addConstants(constants)
      .addOutputLane("a").build();

    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("baseSalary", Field.create(Field.Type.DOUBLE, 100000.25));
      map.put("tax", Field.create(Field.Type.DECIMAL, new BigDecimal(30000.25)));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(3, result.size());
      Assert.assertTrue(result.containsKey("netSalary"));
      Assert.assertEquals(0, new BigDecimal(100000.25 + 2000 - 30000.25).compareTo((BigDecimal) result.get("netSalary").getValue()));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testComplexExpression() throws StageException {

    ExpressionProcessorConfig complexExpressionConfig = new ExpressionProcessorConfig();
    complexExpressionConfig.expression = "${((record:value('/baseSalary') * 2) + record:value('/bonus') - (record:value('/perks') / record:value('/bonus')))/2}";
    complexExpressionConfig.fieldToSet = "/complexResult";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(complexExpressionConfig))
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("baseSalary", Field.create(Field.Type.DOUBLE, 100000.25));
      map.put("bonus", Field.create(Field.Type.INTEGER, 2000));
      map.put("perks", Field.create(Field.Type.SHORT, 200));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(4, result.size());
      Assert.assertTrue(result.containsKey("complexResult"));
      Assert.assertEquals(101000.2, result.get("complexResult").getValue()); //((100000.25 * 2) + 2000 - (200 / 2000))/2
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testSubstringExpression() throws StageException {

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${str:substring(record:value('/fullName') , 6, 20)}";
    expressionProcessorConfig.fieldToSet = "/lastName";

    ExpressionProcessorConfig expressionProcessorConfig1 = new ExpressionProcessorConfig();
    expressionProcessorConfig1.expression = "${str:substring(record:value('/fullName') , 10, 20)}";
    expressionProcessorConfig1.fieldToSet = "/empty";

    ExpressionProcessorConfig expressionProcessorConfig2 = new ExpressionProcessorConfig();
    expressionProcessorConfig2.expression = "${str:substring(record:value('/fullName') , 0, 6)}";
    expressionProcessorConfig2.fieldToSet = "/first";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig, expressionProcessorConfig1, expressionProcessorConfig2))
      .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("fullName", Field.create(Field.Type.STRING, "streamsets"));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(4, result.size());
      Assert.assertTrue(result.containsKey("lastName"));
      Assert.assertEquals("sets", result.get("lastName").getValue());
      Assert.assertTrue(result.containsKey("empty"));
      Assert.assertEquals("", result.get("empty").getValue());
      Assert.assertTrue(result.containsKey("first"));
      Assert.assertEquals("stream", result.get("first").getValue());
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testRecordId() throws StageException {

    ExpressionProcessorConfig complexExpressionConfig = new ExpressionProcessorConfig();
    complexExpressionConfig.expression = "${record:id()}";
    complexExpressionConfig.fieldToSet = "/id";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
        .addConfiguration("expressionProcessorConfigs", ImmutableList.of(complexExpressionConfig))
        .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());
      Field field = output.getRecords().get("a").get(0).get();
      Assert.assertTrue(field.getValue() instanceof Map);
      Map<String, Field> result = field.getValueAsMap();
      Assert.assertEquals(1, result.size());
      Assert.assertTrue(result.containsKey("id"));
      Assert.assertEquals("s:1", result.get("id").getValue());
    } finally {
      runner.runDestroy();
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFailingFieldSet() throws StageException {

    ExpressionProcessorConfig complexExpressionConfig = new ExpressionProcessorConfig();
    complexExpressionConfig.expression = "${record:id()}";
    complexExpressionConfig.fieldToSet = "/id/xx";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
        .setOnRecordError(OnRecordError.TO_ERROR)
        .addConfiguration("expressionProcessorConfigs", ImmutableList.of(complexExpressionConfig))
        .addOutputLane("a").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      runner.runProcess(ImmutableList.of(record));
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testEmptyMapAndEmptyList() throws StageException {
    ExpressionProcessorConfig expressionProcessorConfig1 = new ExpressionProcessorConfig();
    expressionProcessorConfig1.expression = "${emptyMap()}";
    expressionProcessorConfig1.fieldToSet = "/d";

    ExpressionProcessorConfig expressionProcessorConfig2 = new ExpressionProcessorConfig();
    expressionProcessorConfig2.expression = "${emptyList()}";
    expressionProcessorConfig2.fieldToSet = "/d/e";

    ExpressionProcessorConfig expressionProcessorConfig3 = new ExpressionProcessorConfig();
    expressionProcessorConfig3.expression = "${emptyMap()}";
    expressionProcessorConfig3.fieldToSet = "/d/e[0]";

    ExpressionProcessorConfig expressionProcessorConfig4 = new ExpressionProcessorConfig();
    expressionProcessorConfig4.expression = "${record:value('/a')}";
    expressionProcessorConfig4.fieldToSet = "/d/e[0]/f";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(
        expressionProcessorConfig1, expressionProcessorConfig2, expressionProcessorConfig3, expressionProcessorConfig4))
      .addOutputLane("lane").build();
    runner.runInit();

    try {
      Map<String, Field> map = new LinkedHashMap<>();
      map.put("a", Field.create("A"));
      map.put("b", Field.create("B"));
      map.put("c", Field.create("C"));
      Record record = RecordCreator.create("s", "s:1");
      record.set(Field.create(map));

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("lane").size());
      Field fieldD = output.getRecords().get("lane").get(0).get("/d");

      Assert.assertTrue(fieldD.getValue() instanceof Map);
      Map<String, Field> result = fieldD.getValueAsMap();
      Field fieldE = result.get("e");
      Assert.assertTrue(fieldE.getValue() instanceof List);
      List<Field> listField = fieldE.getValueAsList();
      Assert.assertTrue(listField.get(0).getValue() instanceof Map);
      Map<String, Field> fieldMap = listField.get(0).getValueAsMap();
      Assert.assertTrue(fieldMap.get("f").getValue() instanceof String);
      Assert.assertEquals(fieldMap.get("f").getValueAsString(), "A");
    } finally {
      runner.runDestroy();
    }
  }

  @Test
  public void testWildCardExpression() throws StageException {

    Field name1 = Field.create("jon");
    Field name2 = Field.create("natty");
    Map<String, Field> nameMap1 = new HashMap<>();
    nameMap1.put("name", name1);
    Map<String, Field> nameMap2 = new HashMap<>();
    nameMap2.put("name", name2);

    Field name3 = Field.create("adam");
    Field name4 = Field.create("hari");
    Map<String, Field> nameMap3 = new HashMap<>();
    nameMap3.put("name", name3);
    Map<String, Field> nameMap4 = new HashMap<>();
    nameMap4.put("name", name4);

    Field name5 = Field.create("madhu");
    Field name6 = Field.create("girish");
    Map<String, Field> nameMap5 = new HashMap<>();
    nameMap5.put("name", name5);
    Map<String, Field> nameMap6 = new HashMap<>();
    nameMap6.put("name", name6);

    Field first = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap1), Field.create(nameMap2)));
    Field second = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap3), Field.create(nameMap4)));
    Field third = Field.create(Field.Type.LIST, ImmutableList.of(Field.create(nameMap5), Field.create(nameMap6)));

    Map<String, Field> noe = new HashMap<>();
    noe.put("streets", Field.create(ImmutableList.of(first, second)));

    Map<String, Field> cole = new HashMap<>();
    cole.put("streets", Field.create(ImmutableList.of(third)));


    Map<String, Field> sfArea = new HashMap<>();
    sfArea.put("noe", Field.create(noe));

    Map<String, Field> utahArea = new HashMap<>();
    utahArea.put("cole", Field.create(cole));


    Map<String, Field> california = new HashMap<>();
    california.put("SanFrancisco", Field.create(sfArea));

    Map<String, Field> utah = new HashMap<>();
    utah.put("SantaMonica", Field.create(utahArea));

    Map<String, Field> map = new LinkedHashMap<>();
    map.put("USA", Field.create(Field.Type.LIST,
      ImmutableList.of(Field.create(california), Field.create(utah))));

    Record record = RecordCreator.create("s", "s:1");
    record.set(Field.create(map));

    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(), "jon");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(), "natty");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(), "adam");
    Assert.assertEquals(record.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(), "hari");
    Assert.assertEquals(record.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString(), "madhu");
    Assert.assertEquals(record.get("/USA[1]/SantaMonica/cole/streets[0][1]/name").getValueAsString(), "girish");

    /* All the field Paths in the record are
        /USA
        /USA[0]
        /USA[0]/SanFrancisco
        /USA[0]/SanFrancisco/noe
        /USA[0]/SanFrancisco/noe/streets
        /USA[0]/SanFrancisco/noe/streets[0]
        /USA[0]/SanFrancisco/noe/streets[0][0]
        /USA[0]/SanFrancisco/noe/streets[0][0]/name
        /USA[0]/SanFrancisco/noe/streets[0][1]
        /USA[0]/SanFrancisco/noe/streets[0][1]/name
        /USA[0]/SanFrancisco/noe/streets[1]
        /USA[0]/SanFrancisco/noe/streets[1][0]
        /USA[0]/SanFrancisco/noe/streets[1][0]/name
        /USA[0]/SanFrancisco/noe/streets[1][1]
        /USA[0]/SanFrancisco/noe/streets[1][1]/name
        /USA[1]
        /USA[1]/SantaMonica
        /USA[1]/SantaMonica/cole
        /USA[1]/SantaMonica/cole/streets
        /USA[1]/SantaMonica/cole/streets[0]
        /USA[1]/SantaMonica/cole/streets[0][0]
        /USA[1]/SantaMonica/cole/streets[0][0]/name
        /USA[1]/SantaMonica/cole/streets[0][1]
        /USA[1]/SantaMonica/cole/streets[0][1]/name
      */

    ExpressionProcessorConfig expressionProcessorConfig = new ExpressionProcessorConfig();
    expressionProcessorConfig.expression = "${\"Razor\"}";
    expressionProcessorConfig.fieldToSet = "/USA[*]/SanFrancisco/*/streets[*][*]/name";

    ProcessorRunner runner = new ProcessorRunner.Builder(ExpressionDProcessor.class)
      .addConfiguration("expressionProcessorConfigs", ImmutableList.of(expressionProcessorConfig))
      .addOutputLane("a").build();
    runner.runInit();

    try {

      StageRunner.Output output = runner.runProcess(ImmutableList.of(record));
      Assert.assertEquals(1, output.getRecords().get("a").size());

      Record resultRecord = output.getRecords().get("a").get(0);
      Assert.assertEquals(resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][0]/name").getValueAsString(),
        "Razor");
      Assert.assertEquals(resultRecord.get("/USA[0]/SanFrancisco/noe/streets[0][1]/name").getValueAsString(),
        "Razor");

      Assert.assertEquals(resultRecord.get("/USA[0]/SanFrancisco/noe/streets[1][0]/name").getValueAsString(),
        "Razor");
      Assert.assertEquals(resultRecord.get("/USA[0]/SanFrancisco/noe/streets[1][1]/name").getValueAsString(),
        "Razor");

      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][0]/name").getValueAsString(),
        "madhu");
      Assert.assertEquals(resultRecord.get("/USA[1]/SantaMonica/cole/streets[0][1]/name").getValueAsString(),
        "girish");

    } finally {
      runner.runDestroy();
    }
  }

}
