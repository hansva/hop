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

package org.apache.hop.mongo.wrapper;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.Collections;
import java.util.List;

public class DefaultMongoClientFactory implements MongoClientFactory {

  @Override
  public MongoClient getMongoClient(
      List<ServerAddress> serverAddressList,
      List<MongoCredential> credList,
      MongoClientSettings.Builder settingsBuilder,
      boolean useReplicaSet) {
    // Mongo's java driver will discover all replica set or shard
    // members (Mongos) automatically when MongoClient is constructed
    // using a list of ServerAddresses. The javadocs state that MongoClient
    // should be constructed using a SingleServer address instance (rather
    // than a list) when connecting to a stand-alone host - this is why
    // we differentiate here between a list containing one ServerAddress
    // and a single ServerAddress instance via the useAllReplicaSetMembers
    // flag.

    List<ServerAddress> hosts =
        (useReplicaSet || serverAddressList.size() > 1)
            ? serverAddressList
            : Collections.singletonList(serverAddressList.get(0));

    settingsBuilder.applyToClusterSettings(
        builder -> builder.hosts(hosts).mode(getClusterMode(hosts, useReplicaSet)));

    // Apply credentials if provided
    if (credList != null && !credList.isEmpty()) {
      // MongoDB 5.x only supports a single credential
      settingsBuilder.credential(credList.get(0));
    }

    return MongoClients.create(settingsBuilder.build());
  }

  private com.mongodb.connection.ClusterConnectionMode getClusterMode(
      List<ServerAddress> hosts, boolean useReplicaSet) {
    if (useReplicaSet || hosts.size() > 1) {
      return com.mongodb.connection.ClusterConnectionMode.MULTIPLE;
    }
    return com.mongodb.connection.ClusterConnectionMode.SINGLE;
  }

  @Override
  public MongoClient getMongoClient(
      String connectionString, MongoClientSettings.Builder settingsBuilder) {
    ConnectionString connString = new ConnectionString(connectionString);

    if (settingsBuilder != null) {
      // Apply the connection string settings first, then overlay any additional settings
      settingsBuilder.applyConnectionString(connString);
      return MongoClients.create(settingsBuilder.build());
    } else {
      // Just use the connection string directly
      return MongoClients.create(connString);
    }
  }
}
