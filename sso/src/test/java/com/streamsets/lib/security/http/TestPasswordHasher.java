/**
 * Copyright 2016 StreamSets Inc.
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
package com.streamsets.lib.security.http;

import com.streamsets.datacollector.util.Configuration;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.crypto.spec.PBEKeySpec;

public class TestPasswordHasher {

  @Test
  public void testHashAlgorithm() {
    Assert.assertEquals(PasswordHasher.HASH_ALGORITHM, PasswordHasher.SECRET_KEY_FACTORY.getAlgorithm());
  }

  @Test
  public void testConfiguration() {
    PasswordHasher hasher = new PasswordHasher(new Configuration());
    Assert.assertEquals("v2", hasher.getCurrentVersion());
    Assert.assertEquals(100000, hasher.getIterations());
    Assert.assertEquals(256, hasher.getKeyLength());
    Assert.assertEquals(32, hasher.getSaltLength());
    Assert.assertEquals(32, hasher.getSalt().length);

    Configuration configuration = new Configuration();
    configuration.set(PasswordHasher.ITERATIONS_KEY, 1);
    configuration.set(PasswordHasher.KEY_LENGTH_KEY, 16);
    hasher = new PasswordHasher(configuration);
    Assert.assertEquals(1, hasher.getIterations());
    Assert.assertEquals(16, hasher.getKeyLength());
    Assert.assertEquals(2, hasher.getSaltLength());
    Assert.assertEquals(2, hasher.getSalt().length);
  }

  @Test
  public void testPasswordHashDefault() throws Exception {
    Configuration configuration = new Configuration();
    configuration.set(PasswordHasher.ITERATIONS_KEY, 1);
    PasswordHasher hasher = new PasswordHasher(configuration);
    String currentVersion = hasher.getCurrentVersion();

    String passwordHash = hasher.getPasswordHash("user", "foo");
    Assert.assertEquals(hasher.getCurrentVersion(), hasher.getHashVersion(passwordHash));

    Assert.assertTrue(passwordHash.startsWith(currentVersion + ":" + hasher.getIterations() + ":"));
    String[] parts = passwordHash.split(":");
    Assert.assertEquals(4, parts.length);

    int iterations = Integer.parseInt(parts[1]);
    byte[] salt = Hex.decodeHex(parts[2].toCharArray());

    PBEKeySpec spec = new PBEKeySpec(
        hasher.getValueToHash(currentVersion, "user", "foo").toCharArray(),
        salt,
        iterations,
        hasher.getKeyLength()
    );
    byte[] hash = PasswordHasher.SECRET_KEY_FACTORY.generateSecret(spec).getEncoded();
    String hashHex = Hex.encodeHexString(hash);
    Assert.assertEquals(parts[3], hashHex);

    //valid u/p
    Assert.assertTrue(hasher.verify(passwordHash, "user", "foo"));

    // invalid u valid p, V2 catches this
    Assert.assertFalse(hasher.verify(passwordHash, "userx", "foo"));

    // invalid p
    Assert.assertFalse(hasher.verify(passwordHash, "user", "bar"));
  }

  @Test
  public void testPasswordHashV1() throws Exception {
    Configuration configuration = new Configuration();
    configuration.set(PasswordHasher.ITERATIONS_KEY, 1);
    PasswordHasher hasher = new PasswordHasher(configuration);
    String passwordHash = hasher.computeHash(
        PasswordHasher.V1,
        2,
        hasher.getSalt(),
        hasher.getValueToHash(PasswordHasher.V1, "user", "foo")
    );

    //valid u/p
    Assert.assertTrue(hasher.verify(passwordHash, "user", "foo"));

    // invalid u valid p, V2 catches this
    Assert.assertTrue(hasher.verify(passwordHash, "userx", "foo"));

    // invalid p
    Assert.assertFalse(hasher.verify(passwordHash, "user", "bar"));
  }

  @Test
  public void testCreateRandomValueGeneration() throws Exception {
    Configuration conf = new Configuration();
    conf.set(PasswordHasher.ITERATIONS_KEY, 1);
    PasswordHasher hasher = new PasswordHasher(conf);
    String[] random = hasher.createRandomValueAndHash();
    Assert.assertNotNull(random);
    Assert.assertEquals(2, random.length);
    Assert.assertNotNull(random[0]);
    Assert.assertNotNull(random[1]);
    hasher.verify(random[1], random[0], random[0]);
  }

  @Test
  public void testVerifyCaching() throws Exception {
    Configuration conf = new Configuration();
    conf.set(PasswordHasher.ITERATIONS_KEY, 1);
    PasswordHasher hasher = Mockito.spy(new PasswordHasher(conf));
    String[] valueHash = hasher.getRandomValueAndHash();

    Mockito.reset(hasher);

    // not in cache
    Assert.assertTrue(hasher.verify(valueHash[1], valueHash[0], valueHash[0]));
    Mockito.verify(hasher, Mockito.times(2)).getVerifyCache();
    Mockito
        .verify(hasher, Mockito.times(1))
        .computeHash(Mockito.eq("v2"), Mockito.anyInt(), Mockito.any(byte[].class), Mockito.anyString());

    Mockito.reset(hasher);
    // in cache
    Assert.assertTrue(hasher.verify(valueHash[1], valueHash[0], valueHash[0]));
    Mockito.verify(hasher, Mockito.times(1)).getVerifyCache();
    Mockito
        .verify(hasher, Mockito.times(0))
        .computeHash(Mockito.eq("v2"), Mockito.anyInt(), Mockito.any(byte[].class), Mockito.anyString());

  }

}
