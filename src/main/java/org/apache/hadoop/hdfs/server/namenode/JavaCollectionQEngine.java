/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hadoop.hdfs.server.namenode;

import static com.googlecode.cqengine.query.QueryFactory.and;
import static com.googlecode.cqengine.query.QueryFactory.attribute;
import static com.googlecode.cqengine.query.QueryFactory.contains;
import static com.googlecode.cqengine.query.QueryFactory.endsWith;
import static com.googlecode.cqengine.query.QueryFactory.equal;
import static com.googlecode.cqengine.query.QueryFactory.greaterThan;
import static com.googlecode.cqengine.query.QueryFactory.greaterThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.lessThan;
import static com.googlecode.cqengine.query.QueryFactory.lessThanOrEqualTo;
import static com.googlecode.cqengine.query.QueryFactory.not;
import static com.googlecode.cqengine.query.QueryFactory.startsWith;

import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.persistence.wrapping.WrappingPersistence;
import com.googlecode.cqengine.query.Query;
import com.googlecode.cqengine.query.parser.sql.SQLParser;
import com.googlecode.cqengine.resultset.ResultSet;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.server.namenode.queries.FileTypeHistogram;
import org.apache.hadoop.hdfs.server.namenode.queries.Histograms;
import org.apache.hadoop.hdfs.server.namenode.queries.MemorySizeHistogram;
import org.apache.hadoop.hdfs.server.namenode.queries.SpaceSizeHistogram;
import org.apache.hadoop.hdfs.server.namenode.queries.TimeHistogram;
import org.apache.hadoop.io.IOUtils;
import org.apache.http.HttpStatus;

public class JavaCollectionQEngine extends AbstractQueryEngine {

  private final SimpleAttribute<INode, Long> id =
      attribute("id", node -> getFilterFunctionToLongForINode("id").apply(node));
  private final SimpleAttribute<INode, Long> accessTime =
      attribute("accessTime", node -> getFilterFunctionToLongForINode("accessTime").apply(node));
  private final SimpleAttribute<INode, Long> modTime =
      attribute("modTime", node -> getFilterFunctionToLongForINode("modTime").apply(node));
  private final SimpleAttribute<INode, Long> fileSize =
      attribute("fileSize", node -> getFilterFunctionToLongForINode("fileSize").apply(node));
  private final SimpleAttribute<INode, Long> diskspaceConsumed =
      attribute(
          "diskspaceConsumed",
          node -> getFilterFunctionToLongForINode("diskspaceConsumed").apply(node));
  private final SimpleAttribute<INode, Long> memoryConsumed =
      attribute(
          "memoryConsumed", node -> getFilterFunctionToLongForINode("memoryConsumed").apply(node));
  private final SimpleAttribute<INode, Long> fileReplica =
      attribute("fileReplica", node -> getFilterFunctionToLongForINode("fileReplica").apply(node));
  private final SimpleAttribute<INode, Long> numBlocks =
      attribute("numBlocks", node -> getFilterFunctionToLongForINode("numBlocks").apply(node));
  private final SimpleAttribute<INode, Long> numReplicas =
      attribute("numReplicas", node -> getFilterFunctionToLongForINode("numReplicas").apply(node));
  private final SimpleAttribute<INode, Long> depth =
      attribute("depth", node -> getFilterFunctionToLongForINode("depth").apply(node));
  private final SimpleAttribute<INode, Long> permission =
      attribute("permission", node -> getFilterFunctionToLongForINode("permission").apply(node));
  private final SimpleAttribute<INode, String> user =
      attribute("user", node -> getFilterFunctionToStringForINode("user").apply(node));
  private final SimpleAttribute<INode, String> group =
      attribute("group", node -> getFilterFunctionToStringForINode("group").apply(node));
  private final SimpleAttribute<INode, String> name =
      attribute("name", node -> getFilterFunctionToStringForINode("name").apply(node));
  private final SimpleAttribute<INode, String> path =
      attribute("path", node -> getFilterFunctionToStringForINode("path").apply(node));
  private final SimpleAttribute<INode, Date> modDate =
      attribute(
          "modDate",
          node -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            String modDateInt = getFilterFunctionToStringForINode("modDate").apply(node);
            try {
              return sdf.parse(modDateInt);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
  private final SimpleAttribute<INode, Date> accessDate =
      attribute(
          "accessDate",
          node -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            String modDateInt = getFilterFunctionToStringForINode("accessDate").apply(node);
            try {
              return sdf.parse(modDateInt);
            } catch (ParseException e) {
              throw new RuntimeException(e);
            }
          });
  private final SimpleAttribute<INode, Boolean> isUnderConstruction =
      attribute(
          "isUnderConstruction",
          node -> getFilterFunctionToBooleanForINode("isUnderConstruction").apply(node));
  private final SimpleAttribute<INode, Boolean> isWithSnapshot =
      attribute(
          "isWithSnapshot",
          node -> getFilterFunctionToBooleanForINode("isWithSnapshot").apply(node));
  private final SimpleAttribute<INode, Boolean> hasAcl =
      attribute("hasAcl", node -> getFilterFunctionToBooleanForINode("hasAcl").apply(node));
  private final SimpleAttribute<INode, Boolean> hasQuota =
      attribute("hasQuota", node -> getFilterFunctionToBooleanForINode("hasQuota").apply(node));
  private SimpleAttribute<INode, Long> dirNumChildren;
  private SimpleAttribute<INode, Long> dirSubTreeSize;
  private SimpleAttribute<INode, Long> dirSubTreeNumFiles;
  private SimpleAttribute<INode, Long> dirSubTreeNumDirs;
  private SimpleAttribute<INode, Long> storageType;

  private IndexedCollection<INode> indexedFiles;
  private IndexedCollection<INode> indexedDirs;

  @Override // QueryEngine
  public void setContexts(NameNodeLoader loader, VersionInterface versionLoader) {
    this.nameNodeLoader = loader;
    this.versionLoader = versionLoader;

    dirNumChildren =
        attribute(
            "dirNumChildren",
            node -> versionLoader.getFilterFunctionToLongForINode("dirNumChildren").apply(node));
    dirSubTreeSize =
        attribute(
            "dirSubTreeSize",
            node -> versionLoader.getFilterFunctionToLongForINode("dirSubTreeSize").apply(node));
    dirSubTreeNumFiles =
        attribute(
            "dirSubTreeNumFiles",
            node ->
                versionLoader.getFilterFunctionToLongForINode("dirSubTreeNumFiles").apply(node));
    dirSubTreeNumDirs =
        attribute(
            "dirSubTreeNumDirs",
            node -> versionLoader.getFilterFunctionToLongForINode("dirSubTreeNumDirs").apply(node));
    storageType =
        attribute(
            "storageType",
            node -> versionLoader.getFilterFunctionToLongForINode("storageType").apply(node));

    Collection<INode> files = loader.getINodeSetInternal("files");
    Collection<INode> dirs = loader.getINodeSetInternal("dirs");

    indexedFiles =
        new ConcurrentIndexedCollection<>(
            WrappingPersistence.aroundCollectionOnPrimaryKey(files, id));
    //    indexedFiles.addIndex(UniqueIndex.onAttribute(id));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(accessTime));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(modTime));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(fileSize));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(diskspaceConsumed));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(memoryConsumed));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(fileReplica));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(numBlocks));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(numReplicas));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(depth));
    //    indexedFiles.addIndex(NavigableIndex.onAttribute(permission));
    //    indexedFiles.addIndex(SuffixTreeIndex.onAttribute(user));
    //    indexedFiles.addIndex(SuffixTreeIndex.onAttribute(group));
    //    indexedFiles.addIndex(SuffixTreeIndex.onAttribute(name));
    //    indexedFiles.addIndex(SuffixTreeIndex.onAttribute(path));
    //    indexedFiles.addIndex(HashIndex.onAttribute(isUnderConstruction));
    //    indexedFiles.addIndex(HashIndex.onAttribute(isWithSnapshot));
    //    indexedFiles.addIndex(HashIndex.onAttribute(hasAcl));

    indexedDirs =
        new ConcurrentIndexedCollection<>(
            WrappingPersistence.aroundCollectionOnPrimaryKey(dirs, id));
    //    indexedDirs.addIndex(UniqueIndex.onAttribute(id));
    //    indexedDirs.addIndex(NavigableIndex.onAttribute(accessTime));
    //    indexedDirs.addIndex(NavigableIndex.onAttribute(modTime));
    //    indexedDirs.addIndex(NavigableIndex.onAttribute(memoryConsumed));
    //    indexedDirs.addIndex(NavigableIndex.onAttribute(depth));
    //    indexedDirs.addIndex(NavigableIndex.onAttribute(permission));
    //    indexedDirs.addIndex(SuffixTreeIndex.onAttribute(user));
    //    indexedDirs.addIndex(SuffixTreeIndex.onAttribute(group));
    //    indexedDirs.addIndex(SuffixTreeIndex.onAttribute(name));
    //    indexedDirs.addIndex(SuffixTreeIndex.onAttribute(path));
    //    indexedDirs.addIndex(HashIndex.onAttribute(hasQuota));
  }

  @Override // QueryEngine
  public Collection<INode> getINodeSet(String set) {
    long start = System.currentTimeMillis();
    Collection<INode> inodes;
    switch (set) {
      case "all":
        inodes =
            new ConcurrentIndexedCollection<>(
                WrappingPersistence.aroundCollectionOnPrimaryKey(
                    nameNodeLoader.getINodeSetInternal("all"), id));
        break;
      case "files":
        inodes = indexedFiles;
        break;
      case "dirs":
        inodes = indexedDirs;
        break;
      default:
        throw new IllegalArgumentException(
            "You did not specify a set to use. Please check /sets for available sets.");
    }
    long end = System.currentTimeMillis();
    LOG.info(
        "Fetching indexed set of: {} had result size: {} and took: {} ms.",
        set,
        inodes.size(),
        (end - start));
    return inodes;
  }

  /**
   * Main filter method for filtering down a set of INodes to a smaller subset.
   *
   * @param inodes the main inode set to work on
   * @param filters set of filters to use
   * @param filterOps matching length set of filter operands and operators
   * @return the filtered set of inodes
   */
  @Override // QueryEngine
  public Collection<INode> combinedFilter(
      Collection<INode> inodes, String[] filters, String[] filterOps) {
    IndexedCollection<INode> indexedINodes;
    if (!(inodes instanceof IndexedCollection)) {
      indexedINodes =
          new ConcurrentIndexedCollection<>(
              WrappingPersistence.aroundCollectionOnPrimaryKey(inodes, id));
    } else {
      indexedINodes = (IndexedCollection<INode>) inodes;
    }

    List<Query<INode>> queries = new ArrayList<>();
    for (int i = 0; i < filters.length; i++) {
      String filter = filters[i];
      String[] filterOp = filterOps[i].split(":");
      Query<INode> filterFunc = getFilter(filter, filterOp);
      queries.add(filterFunc);
    }

    long start = System.currentTimeMillis();
    try {
      ResultSet<INode> result;
      switch (queries.size()) {
        case 0:
          return inodes;
        case 1:
          result = indexedINodes.retrieve(queries.get(0));
          break;
        case 2:
          result = indexedINodes.retrieve(and(queries.get(0), queries.get(1)));
          break;
        default:
          result =
              indexedINodes.retrieve(
                  and(queries.get(0), queries.get(1), queries.subList(2, queries.size())));
          break;
      }
      return result.stream().collect(Collectors.toSet());
    } finally {
      long end = System.currentTimeMillis();
      LOG.info(
          "Performing filters: {} with filterOps: {} took: {} ms.",
          Arrays.asList(filters),
          Arrays.asList(filterOps),
          (end - start));
    }
  }

  /**
   * Perform the find operation on a /filter endpoint call.
   *
   * @param inodes set of inodes to work on
   * @param find the find operation to perform
   * @return the result of the find operation
   */
  @Override // QueryEngine
  public Collection<INode> findFilter(Collection<INode> inodes, String find) {
    if (find == null || find.isEmpty()) {
      return inodes;
    }

    String[] findOps = find.split(":");
    Function<INode, Long> findToLong = getFilterFunctionToLongForINode(findOps[1]);

    long start = System.currentTimeMillis();
    Optional<INode> optional;
    try {
      Stream<INode> stream = inodes.parallelStream();
      switch (findOps[0]) {
        case "max":
          optional = stream.max(Comparator.comparingLong(findToLong::apply));
          break;
        case "min":
          optional = stream.min(Comparator.comparingLong(findToLong::apply));
          break;
        default:
          throw new IllegalArgumentException("Unknown find query type: " + findOps[0]);
      }
    } finally {
      long end = System.currentTimeMillis();
      LOG.info("Performing find: {} took: {} ms.", Arrays.asList(findOps), (end - start));
    }

    return optional.<Collection<INode>>map(Collections::singleton).orElseGet(Collections::emptySet);
  }

  private Query<INode> getFilter(String filter, String[] filterOps) {
    long start = System.currentTimeMillis();
    try {
      // Values for all other filters
      String op = filterOps[0];
      String opValue = filterOps[1];

      // Long value filters
      Attribute<INode, Long> longAttribute = getLongAttributeForINode(filter);
      if (longAttribute != null) {
        return getQueryForLong(longAttribute, Long.parseLong(opValue), op);
      }

      // String value filters
      Attribute<INode, String> stringAttribute = getStringAttributeForINode(filter);
      if (stringAttribute != null) {
        return getQueryForString(stringAttribute, opValue, op);
      }

      // Boolean value filters
      Attribute<INode, Boolean> booleanAttribute = getBooleanAttributeForINode(filter);
      if (booleanAttribute != null) {
        return getQueryForBoolean(booleanAttribute, opValue, op);
      }

      // Date value filters
      Attribute<INode, Date> dateAttribute = getDateAttributeForINode(filter);
      if (dateAttribute != null) {
        return getQueryForDate(dateAttribute, opValue, op);
      }

      throw new IllegalArgumentException(
          "Failed to determine filter: "
              + filter
              + ", with operations: "
              + Arrays.asList(filterOps)
              + ".\nCheck your filter arguments."
              + "\nPossible filters and operations available at /filters and /filterOps.");
    } finally {
      long end = System.currentTimeMillis();
      LOG.info(
          "Obtaining filter: {} with filterOps:{} took: {} ms.",
          filter,
          Arrays.asList(filterOps),
          (end - start));
    }
  }

  private Query<INode> getQueryForDate(
      Attribute<INode, Date> dateAttribute, String value, String op) {
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    Date valueDate;
    switch (op) {
      case "dateEq":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return equal(dateAttribute, valueDate);
      case "dateNotEq":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return not(equal(dateAttribute, valueDate));
      case "dateLt":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return lessThan(dateAttribute, valueDate);
      case "dateStart":
      case "dateLte":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return lessThanOrEqualTo(dateAttribute, valueDate);
      case "dateGt":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return greaterThan(dateAttribute, valueDate);
      case "dateEnd":
      case "dateGte":
        try {
          valueDate = sdf.parse(value);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
        return greaterThanOrEqualTo(dateAttribute, valueDate);
      default:
        return null;
    }
  }

  private Attribute<INode, Date> getDateAttributeForINode(String filter) {
    switch (filter) {
      case "modDate":
        return modDate;
      case "accessDate":
        return accessDate;
      default:
        return null;
    }
  }

  private Query<INode> getQueryForBoolean(
      Attribute<INode, Boolean> booleanAttribute, String value, String op) {
    switch (op) {
      case "eq":
        return equal(booleanAttribute, Boolean.valueOf(value));
      case "notEq":
        return not(equal(booleanAttribute, Boolean.valueOf(value)));
      default:
        return null;
    }
  }

  private Attribute<INode, Boolean> getBooleanAttributeForINode(String filter) {
    switch (filter) {
      case "isUnderConstruction":
        return isUnderConstruction;
      case "isWithSnapshot":
        return isWithSnapshot;
      case "hasAcl":
        return hasAcl;
      case "hasQuota":
        return hasQuota;
      default:
        return null;
    }
  }

  private Query<INode> getQueryForString(
      Attribute<INode, String> stringAttribute, String value, String op) {
    switch (op) {
      case "eq":
        return equal(stringAttribute, value);
      case "notEq":
        return not(equal(stringAttribute, value));
      case "startsWith":
        return startsWith(stringAttribute, value);
      case "notStartsWith":
        return not(startsWith(stringAttribute, value));
      case "endsWith":
        return endsWith(stringAttribute, value);
      case "notEndsWith":
        return not(endsWith(stringAttribute, value));
      case "contains":
        return contains(stringAttribute, value);
      case "notContains":
        return not(contains(stringAttribute, value));
      default:
        return null;
    }
  }

  private Attribute<INode, String> getStringAttributeForINode(String filter) {
    switch (filter) {
      case "user":
        return user;
      case "group":
        return group;
      case "name":
        return name;
      case "path":
        return path;
      default:
        return null;
    }
  }

  private Query<INode> getQueryForLong(
      Attribute<INode, Long> longAttribute, long value, String op) {
    switch (op) {
      case "eq":
        return equal(longAttribute, value);
      case "gt":
        return greaterThan(longAttribute, value);
      case "gte":
        return greaterThanOrEqualTo(longAttribute, value);
      case "lt":
        return lessThan(longAttribute, value);
      case "lte":
        return lessThanOrEqualTo(longAttribute, value);
      case "minutesAgo":
        return greaterThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(value));
      case "hoursAgo":
        return greaterThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(value));
      case "daysAgo":
        return greaterThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(value));
      case "monthsAgo":
        return greaterThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30 * value));
      case "yearsAgo":
        return greaterThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * value));
      case "olderThanMinutes":
        return lessThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(value));
      case "olderThanHours":
        return lessThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(value));
      case "olderThanDays":
        return lessThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(value));
      case "olderThanMonths":
        return lessThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30 * value));
      case "olderThanYears":
        return lessThanOrEqualTo(
            longAttribute, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * value));
      default:
        return null;
    }
  }

  private Attribute<INode, Long> getLongAttributeForINode(String filter) {
    switch (filter) {
      case "id":
        return id;
      case "accessTime":
        return accessTime;
      case "modTime":
        return modTime;
      case "fileSize":
        return fileSize;
      case "diskspaceConsumed":
        return diskspaceConsumed;
      case "memoryConsumed":
        return memoryConsumed;
      case "fileReplica":
        return fileReplica;
      case "numBlocks":
        return numBlocks;
      case "numReplicas":
        return numReplicas;
      case "depth":
        return depth;
      case "permission":
        return permission;
      case "dirNumChildren":
        return dirNumChildren;
      case "dirSubTreeSize":
        return dirSubTreeSize;
      case "dirSubTreeNumFiles":
        return dirSubTreeNumFiles;
      case "dirSubTreeNumDirs":
        return dirSubTreeNumDirs;
      case "storageType":
        return storageType;
      default:
        return null;
    }
  }

  /**
   * Performs a summation against a collection of INodes.
   *
   * @param inodes the inodes to sum on
   * @param sum the type of summation to perform
   * @return the resulting sum as a long
   */
  @Override // QueryEngine
  public Long sum(Collection<INode> inodes, String sum) {
    long startTime = System.currentTimeMillis();
    try {
      Function<Collection<INode>, Long> sumFunction = getSumFunctionForCollection(sum);
      return sumFunction.apply(inodes);
    } finally {
      long endTime = System.currentTimeMillis();
      LOG.info("Performing sum: {} took: {} ms.", sum, (endTime - startTime));
    }
  }

  /**
   * Get a Function that performs a summation on an entire INode collection to a single Long.
   *
   * @param sum the sum to look for
   * @return the function representing the summation against the INode collection
   */
  private Function<Collection<INode>, Long> getSumFunctionForCollection(String sum) {
    switch (sum) {
      case "count":
        return collection -> ((long) collection.size());
      case "fileSize":
        return collection ->
            collection.parallelStream().mapToLong(node -> node.asFile().computeFileSize()).sum();
      case "diskspaceConsumed":
        return collection ->
            collection
                .parallelStream()
                .mapToLong(
                    node -> node.asFile().computeFileSize() * node.asFile().getFileReplication())
                .sum();
      case "blockSize":
        return collection ->
            collection
                .parallelStream()
                .mapToLong(node -> node.asFile().getPreferredBlockSize())
                .sum();
      case "numBlocks":
        return collection ->
            collection.parallelStream().mapToLong(node -> node.asFile().numBlocks()).sum();
      case "numReplicas":
        return collection ->
            collection
                .parallelStream()
                .mapToLong(
                    node -> ((long) node.asFile().numBlocks() * node.asFile().getFileReplication()))
                .sum();
      case "memoryConsumed":
        return collection ->
            collection
                .parallelStream()
                .mapToLong(
                    node -> {
                      long inodeSize = 100L;
                      if (node.isFile()) {
                        inodeSize += node.asFile().numBlocks() * 150L;
                      }
                      return inodeSize;
                    })
                .sum();
      default:
        throw new IllegalArgumentException(
            "Could not determine sum type: " + sum + ".\nPlease check /sums for available sums.");
    }
  }

  /**
   * Get a Function that converts a String into a Boolean expression.
   *
   * @param value the value to compute against
   * @param op the operation to perform
   * @return the function representing a String to Boolean transformation
   */
  @Override // QueryEngine
  public Function<String, Boolean> getFilterFunctionForString(String value, String op) {
    switch (op) {
      case "eq":
        return s -> s.equals(value);
      case "notEq":
        return s -> !s.equals(value);
      case "startsWith":
        return s -> s.startsWith(value);
      case "notStartsWith":
        return s -> !s.startsWith(value);
      case "endsWith":
        return s -> s.endsWith(value);
      case "notEndsWith":
        return s -> !s.endsWith(value);
      case "contains":
        return s -> s.contains(value);
      case "notContains":
        return s -> !s.contains(value);
      case "dateEq":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return nodeDate.equals(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      case "dateNotEq":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return !nodeDate.equals(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      case "dateLt":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return nodeDate.before(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      case "dateStart":
      case "dateLte":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return nodeDate.before(valueDate) || nodeDate.equals(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      case "dateGt":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return nodeDate.after(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      case "dateEnd":
      case "dateGte":
        return s -> {
          SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
          try {
            Date nodeDate = sdf.parse(s);
            Date valueDate = sdf.parse(value);
            return nodeDate.after(valueDate) || nodeDate.equals(valueDate);
          } catch (ParseException e) {
            throw new RuntimeException(e);
          }
        };
      default:
        throw new IllegalArgumentException(
            "Failed to determine String filter operation.\n"
                + "Please check /filterOps and use operations meant for Strings.");
    }
  }

  /**
   * Get a Function that converts a Boolean into a Boolean expression.
   *
   * @param value the value to compute against
   * @param op the operation to perform
   * @return the function representing a Boolean to Boolean transformation
   */
  @Override // QueryEngine
  public Function<Boolean, Boolean> getFilterFunctionForBoolean(Boolean value, String op) {
    switch (op) {
      case "eq":
        return b -> b == value;
      case "notEq":
        return b -> b != value;
      default:
        throw new IllegalArgumentException(
            "Failed to determine Boolean filter operation.\n"
                + "Please check /filterOps and use operations meant for Booleans.");
    }
  }

  /**
   * Get a Function that converts a Long into a Boolean expression.
   *
   * @param value the value to compute against
   * @param op the operation to perform
   * @return the function representing a Long to Boolean transformation
   */
  @Override // QueryEngine
  public Function<Long, Boolean> getFilterFunctionForLong(Long value, String op) {
    switch (op) {
      case "lt":
        return l -> l < value;
      case "gt":
        return l -> l > value;
      case "eq":
        return l -> l.longValue() == value.longValue();
      case "notEq":
        return l -> l.longValue() != value.longValue();
      case "lte":
        return l -> l <= value;
      case "gte":
        return l -> l >= value;
      case "minutesAgo":
        return l -> l >= System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(value);
      case "hoursAgo":
        return l -> l >= System.currentTimeMillis() - TimeUnit.HOURS.toMillis(value);
      case "daysAgo":
        return l -> l >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(value);
      case "monthsAgo":
        return l -> l >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30 * value);
      case "yearsAgo":
        return l -> l >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * value);
      case "olderThanMinutes":
        return l -> l <= System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(value);
      case "olderThanHours":
        return l -> l <= System.currentTimeMillis() - TimeUnit.HOURS.toMillis(value);
      case "olderThanDays":
        return l -> l <= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(value);
      case "olderThanMonths":
        return l -> l <= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30 * value);
      case "olderThanYears":
        return l -> l <= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * value);
      default:
        throw new IllegalArgumentException(
            "Failed to determine Long filter operation.\n"
                + "Please check /filterOps and use operations meant for Longs.");
    }
  }

  private Function<INode, Long> getTransformFunction(
      Function<INode, Long> stdFunc,
      Map<String, Function<INode, Long>> transformMap,
      String transformKey) {
    if (transformMap.containsKey(transformKey)) {
      LOG.info("Function transformed for: {}", transformKey);
      return transformMap.get(transformKey);
    }
    return stdFunc;
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents diskspace consumed.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @param transformMap a transform to overlay during histogram processing
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> diskspaceConsumedHistogram(
      Collection<INode> inodes,
      String sum,
      String find,
      Map<String, Function<INode, Long>> transformMap) {
    if (find == null || find.length() == 0) {
      return diskspaceConsumedHistogramCpu(inodes, sum, transformMap);
    }
    return diskspaceConsumedHistogramCpuWithFind(inodes, find, transformMap);
  }

  private Map<String, Long> diskspaceConsumedHistogramCpu(
      Collection<INode> inodes, String sum, Map<String, Function<INode, Long>> transformMap) {
    Function<INode, Long> binFunc =
        getTransformFunction(
            getFilterFunctionToLongForINode("diskspaceConsumed"),
            transformMap,
            "diskspaceConsumed");
    Function<INode, Long> sumFunc =
        getTransformFunction(getSumFunctionForINode(sum), transformMap, sum);
    return filteringHistogram(
        inodes,
        sum,
        sumFunc,
        binFunc,
        SpaceSizeHistogram.getBinsArray(),
        SpaceSizeHistogram.getKeys());
  }

  private Map<String, Long> diskspaceConsumedHistogramCpuWithFind(
      Collection<INode> inodes, String find, Map<String, Function<INode, Long>> transformMap) {
    Function<INode, Long> binFunc =
        getTransformFunction(
            getFilterFunctionToLongForINode("diskspaceConsumed"),
            transformMap,
            "diskspaceConsumed");
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];
    return filteringHistogramWithFind(
        inodes,
        findField,
        findOp,
        getFilterFunctionToLongForINode(findField),
        binFunc,
        SpaceSizeHistogram.getBinsArray(),
        SpaceSizeHistogram.getKeys());
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents memory consumed.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> memoryConsumedHistogram(
      Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return memoryConsumedHistogramCpu(inodes, sum);
    } else {
      return memoryConsumedHistogramCpuWithFind(inodes, find);
    }
  }

  private Map<String, Long> memoryConsumedHistogramCpu(Collection<INode> inodes, String sum) {
    Function<INode, Long> memConsumedFunction =
        node -> {
          long inodeSize = 100L;
          if (node.isFile()) {
            inodeSize += node.asFile().numBlocks() * 150L;
          }
          return inodeSize;
        };
    return filteringHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        memConsumedFunction,
        MemorySizeHistogram.getBinsArray(),
        MemorySizeHistogram.getKeys());
  }

  private Map<String, Long> memoryConsumedHistogramCpuWithFind(
      Collection<INode> inodes, String find) {
    Function<INode, Long> memConsumedFunction =
        node -> {
          long inodeSize = 100L;
          if (node.isFile()) {
            inodeSize += node.asFile().numBlocks() * 150L;
          }
          return inodeSize;
        };
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];

    return filteringHistogramWithFind(
        inodes,
        findField,
        findOp,
        getFilterFunctionToLongForINode(findField),
        memConsumedFunction,
        MemorySizeHistogram.getBinsArray(),
        MemorySizeHistogram.getKeys());
  }

  private long[][] fetchDataViaCpu(
      Collection<INode> inodes,
      String sum,
      Function<INode, Long> sumFunc,
      Function<INode, Long> nodeToLong) {
    long start = System.currentTimeMillis();

    long[] data = inodes.parallelStream().mapToLong(nodeToLong::apply).toArray();
    long[] sums = inodes.parallelStream().mapToLong(sumFunc::apply).toArray();

    long end = System.currentTimeMillis();
    LOG.info("Fetching {} data took: {} ms.", sum, (end - start));

    return new long[][] {data, sums};
  }

  private Map<String, Long> strictMappingHistogram(
      Collection<INode> inodes,
      String sum,
      Function<INode, Long> sumFunc,
      Function<INode, Long> nodeToLong) {
    long[][] datas = fetchDataViaCpu(inodes, sum, sumFunc, nodeToLong);
    long[] data = datas[0];
    long[] sums = datas[1];

    long start1 = System.currentTimeMillis();
    int maxId = (int) LongStream.of(data).parallel().max().orElse(data.length);
    long[] histogram;
    try {
      if (data.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else {
        histogram = new long[maxId + 2];
        IntStream.range(0, data.length)
            .parallel()
            .forEach(
                idx -> {
                  int id = (int) data[idx];
                  int chosenBin = maxId + 1;
                  if (id < chosenBin && id != -1) {
                    // Lock in the bin.
                    chosenBin = id;
                  }
                  synchronized (histogram) {
                    histogram[chosenBin] += sums[idx];
                  }
                });
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram took: {} ms.", (end1 - start1));
    LOG.info("Histogram result has size: {}.", histogram.length);
    if (histogram.length > 100) {
      LOG.info("It is too big to console out.");
    } else {
      LOG.info("Result is: {}", Arrays.toString(histogram));
    }
    return Histograms.mapToNonEmptyIndex(histogram);
  }

  private Map<String, Long> strictMappingHistogramWithFind(
      Collection<INode> inodes,
      String findOp,
      String find,
      Function<INode, Long> findFunc,
      Function<INode, Long> nodeToLong) {
    long[][] fetchData = fetchDataViaCpu(inodes, findOp, findFunc, nodeToLong);
    long[] data = fetchData[0];
    long[] sums = fetchData[1];

    long start1 = System.currentTimeMillis();
    long[] histogram;
    try {
      if (data.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else {
        histogram = new long[data.length + 1];
        IntStream.range(0, data.length)
            .parallel()
            .forEach(
                idx -> {
                  int id = (int) data[idx];
                  int chosenBin = data.length;
                  if (id < chosenBin && id != -1) {
                    // Lock in the bin.
                    chosenBin = id;
                  }
                  synchronized (histogram) {
                    long currentVal = histogram[chosenBin];
                    long compareVal = sums[idx];
                    switch (find) {
                      case "max":
                        if (currentVal < compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      case "min":
                        if (compareVal == 0 || currentVal > compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      default:
                        break;
                    }
                  }
                });
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram (with find) took: {} ms.", (end1 - start1));
    LOG.info("Histogram (with find) result has size: {} ", histogram.length);
    if (histogram.length > 100) {
      LOG.info(". It is too big to console out.");
    } else {
      LOG.info(", is: {}", Arrays.toString(histogram));
    }
    return Histograms.mapToNonEmptyIndex(histogram);
  }

  @Override // QueryEngine
  public Map<String, Long> binMappingHistogram(
      Collection<INode> inodes,
      String sum,
      Function<INode, Long> sumFunc,
      Function<INode, Long> nodeToLong,
      Map<String, Long> binKeyMap) {
    long[][] datas = fetchDataViaCpu(inodes, sum, sumFunc, nodeToLong);
    long[] data = datas[0];
    long[] sums = datas[1];
    int length = Math.min(data.length, sums.length);

    long start1 = System.currentTimeMillis();
    long[] histogram;
    try {
      if (data.length == 0 || sums.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else {
        histogram = new long[binKeyMap.size() + 1];
        IntStream.range(0, length)
            .parallel()
            .forEach(
                idx -> {
                  int id = (int) data[idx];
                  int chosenBin = binKeyMap.size();
                  if (id < chosenBin && id != -1) {
                    // Lock in the bin.
                    chosenBin = id;
                  }
                  synchronized (histogram) {
                    histogram[chosenBin] += sums[idx];
                  }
                });
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram took: {} ms.", (end1 - start1));
    LOG.info("Histogram result has size: {}", histogram.length);
    if (histogram.length > 100) {
      LOG.info(". It is too big to console out.");
    } else {
      LOG.info(", is: {}", Arrays.toString(histogram));
    }
    return Histograms.mapByKeys(binKeyMap, histogram);
  }

  @Override // QueryEngine
  public Map<String, Long> binMappingHistogramWithFind(
      Collection<INode> inodes,
      String findFunc,
      Function<INode, Long> findToLong,
      Function<INode, Long> nodeToLong,
      Map<String, Long> binKeyMap) {
    long[][] datas = fetchDataViaCpu(inodes, findFunc, findToLong, nodeToLong);
    long[] data = datas[0];
    long[] sums = datas[1];
    int length = Math.min(data.length, sums.length);

    long start1 = System.currentTimeMillis();
    long[] histogram;
    try {
      if (data.length == 0 || sums.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else if (findFunc.equals("avg")) {
        BigInteger[] bigHistogram = new BigInteger[binKeyMap.size() + 1];
        long[] counts = new long[binKeyMap.size() + 1];
        Arrays.fill(bigHistogram, BigInteger.valueOf(-1));
        IntStream.range(0, length)
            .parallel()
            .forEach(
                idx -> {
                  int id = (int) data[idx];
                  int chosenBin = binKeyMap.size();
                  if (id < chosenBin && id != -1) {
                    // Lock in the bin.
                    chosenBin = id;
                  }
                  synchronized (bigHistogram) {
                    BigInteger currentVal = bigHistogram[chosenBin];
                    long sum = sums[idx];
                    if (currentVal.equals(BigInteger.valueOf(-1))) {
                      bigHistogram[chosenBin] = BigInteger.valueOf(sum);
                    } else {
                      bigHistogram[chosenBin] =
                          bigHistogram[chosenBin].add(BigInteger.valueOf(sum));
                    }
                    counts[chosenBin]++;
                  }
                });
        if (bigHistogram[binKeyMap.size()].equals(BigInteger.valueOf(-1))) {
          bigHistogram[binKeyMap.size()] = BigInteger.ZERO;
        }
        for (int i = 0; i < bigHistogram.length; i++) {
          if (counts[i] != 0) {
            bigHistogram[i] = bigHistogram[i].divide(BigInteger.valueOf(counts[i]));
          }
        }
        histogram = Arrays.stream(bigHistogram).mapToLong(BigInteger::longValue).toArray();
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      } else {
        histogram = new long[binKeyMap.size() + 1];
        Arrays.fill(histogram, -1L);
        IntStream.range(0, length)
            .parallel()
            .forEach(
                idx -> {
                  int id = (int) data[idx];
                  int chosenBin = binKeyMap.size();
                  if (id < chosenBin && id != -1) {
                    // Lock in the bin.
                    chosenBin = id;
                  }
                  synchronized (histogram) {
                    long currentVal = histogram[chosenBin];
                    long compareVal = sums[idx];
                    switch (findFunc) {
                      case "max":
                        if (currentVal < compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      case "min":
                        if (currentVal == -1 || currentVal > compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      default:
                        break;
                    }
                  }
                });
        if (histogram[binKeyMap.size()] == -1) {
          histogram[binKeyMap.size()] = 0;
        }
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram (with find) took: {} ms.", (end1 - start1));
    LOG.info("Histogram (with find) result has size: {}", histogram.length);
    if (histogram.length > 100) {
      LOG.info(". It is too big to console out.");
    } else {
      LOG.info(", is: {}", Arrays.toString(histogram));
    }
    return Histograms.mapByKeys(binKeyMap, histogram);
  }

  private Map<String, Long> filteringHistogram(
      Collection<INode> inodes,
      String sum,
      Function<INode, Long> sumFunc,
      Function<INode, Long> nodeToLong,
      final Long[] binsArray,
      List<String> keys) {
    long[][] datas = fetchDataViaCpu(inodes, sum, sumFunc, nodeToLong);
    long[] data = datas[0];
    long[] sums = datas[1];

    long start1 = System.currentTimeMillis();
    long[] histogram;
    try {
      if (data.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else {
        histogram = new long[binsArray.length + 1];
        IntStream.range(0, data.length)
            .parallel()
            .forEach(
                idx -> {
                  long datum = data[idx];
                  int chosenBin = binsArray.length;
                  for (int i = 0; i < binsArray.length; i++) {
                    if (datum <= binsArray[i] && chosenBin == binsArray.length) {
                      chosenBin = i;
                    }
                  }
                  synchronized (histogram) {
                    histogram[chosenBin] += sums[idx];
                  }
                });
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram took: {} ms.", (end1 - start1));
    LOG.info("Histogram result has size: {}", histogram.length);
    if (histogram.length > 100) {
      LOG.info(". It is too big to console out.");
    } else {
      LOG.info(", is: {}", Arrays.toString(histogram));
    }
    return Histograms.sortByKeys(keys, histogram);
  }

  private Map<String, Long> filteringHistogramWithFind(
      Collection<INode> inodes,
      String findOp,
      String find,
      Function<INode, Long> findFunc,
      Function<INode, Long> nodeToLong,
      final Long[] binsArray,
      List<String> keys) {
    long[][] fetchData = fetchDataViaCpu(inodes, findOp, findFunc, nodeToLong);
    long[] data = fetchData[0];
    long[] sums = fetchData[1];

    long start1 = System.currentTimeMillis();
    long[] histogram;
    try {
      if (data.length == 0) {
        histogram = data;
        LOG.info("Empty data set; skipping.");
      } else if (find.equals("avg")) {
        BigInteger[] bigHistogram = new BigInteger[binsArray.length + 1];
        long[] counts = new long[binsArray.length + 1];
        IntStream.range(0, data.length)
            .parallel()
            .forEach(
                idx -> {
                  long datum = data[idx];
                  int chosenBin = binsArray.length;
                  for (int i = 0; i < binsArray.length; i++) {
                    if (datum <= binsArray[i] && chosenBin == binsArray.length) {
                      chosenBin = i;
                    }
                  }
                  synchronized (bigHistogram) {
                    BigInteger currentVal = bigHistogram[chosenBin];
                    long sum = sums[idx];
                    if (currentVal == null) {
                      bigHistogram[chosenBin] = BigInteger.valueOf(sum);
                    } else {
                      bigHistogram[chosenBin] =
                          bigHistogram[chosenBin].add(BigInteger.valueOf(sum));
                    }
                    counts[chosenBin]++;
                  }
                });
        for (int i = 0; i < bigHistogram.length; i++) {
          if (counts[i] != 0) {
            bigHistogram[i] = bigHistogram[i].divide(BigInteger.valueOf(counts[i]));
          }
        }
        histogram =
            Arrays.stream(bigHistogram).mapToLong(x -> x == null ? 0L : x.longValue()).toArray();
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      } else {
        histogram = new long[binsArray.length + 1];
        IntStream.range(0, data.length)
            .parallel()
            .forEach(
                idx -> {
                  long datum = data[idx];
                  int chosenBin = binsArray.length;
                  for (int i = 0; i < binsArray.length; i++) {
                    if (datum <= binsArray[i] && chosenBin == binsArray.length) {
                      chosenBin = i;
                    }
                  }
                  synchronized (histogram) {
                    long currentVal = histogram[chosenBin];
                    long compareVal = sums[idx];
                    switch (find) {
                      case "max":
                        if (currentVal < compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      case "min":
                        if (compareVal == 0 || currentVal > compareVal) {
                          histogram[chosenBin] = compareVal;
                        }
                        break;
                      default:
                        break;
                    }
                  }
                });
        LOG.info("Histogram returned an array of size: {}", histogram.length);
      }
    } catch (Throwable e) {
      LOG.info("Encountered exception during loading:\n {}", e);
      for (StackTraceElement stacktrace : e.getStackTrace()) {
        LOG.info(stacktrace.toString());
      }
      throw e;
    }
    long end1 = System.currentTimeMillis();
    LOG.info("Histogram (with find) took: {} ms.", (end1 - start1));
    LOG.info("Histogram (with find) result has size: {}", histogram.length);
    if (histogram.length > 100) {
      LOG.info(". It is too big to console out.");
    } else {
      LOG.info(", is: {}", Arrays.toString(histogram));
    }
    return Histograms.sortByKeys(keys, histogram);
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents file size.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> fileSizeHistogram(Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return fileSizeHistogramCpu(inodes, sum);
    }
    return fileSizeHistogramCpuWithFind(inodes, find);
  }

  private Map<String, Long> fileSizeHistogramCpu(Collection<INode> inodes, String sum) {
    return filteringHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        node -> node.asFile().computeFileSize(),
        SpaceSizeHistogram.getBinsArray(),
        SpaceSizeHistogram.getKeys());
  }

  private Map<String, Long> fileSizeHistogramCpuWithFind(Collection<INode> inodes, String find) {
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];
    return filteringHistogramWithFind(
        inodes,
        findField,
        findOp,
        getFilterFunctionToLongForINode(findField),
        node -> node.asFile().computeFileSize(),
        SpaceSizeHistogram.getBinsArray(),
        SpaceSizeHistogram.getKeys());
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents replication factors.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> fileReplicaHistogram(
      Collection<INode> inodes,
      String sum,
      String find,
      Map<String, Function<INode, Long>> transformMap) {
    if (find == null || find.length() == 0) {
      return fileReplicaHistogramCpu(inodes, sum, transformMap);
    }
    return fileReplicaHistogramCpuWithFind(inodes, find);
  }

  private Map<String, Long> fileReplicaHistogramCpu(
      Collection<INode> inodes, String sum, Map<String, Function<INode, Long>> transformMap) {
    Function<INode, Long> binFunc =
        getTransformFunction(
            getFilterFunctionToLongForINode("fileReplica"), transformMap, "fileReplica");
    Function<INode, Long> sumFunc =
        getTransformFunction(getSumFunctionForINode(sum), transformMap, sum);
    return strictMappingHistogram(inodes, sum, sumFunc, binFunc);
  }

  private Map<String, Long> fileReplicaHistogramCpuWithFind(Collection<INode> inodes, String find) {
    Function<INode, Long> binFunc = getFilterFunctionToLongForINode("fileReplica");
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];
    Function<INode, Long> findFunc = getFilterFunctionToLongForINode(findField);
    return strictMappingHistogramWithFind(inodes, findField, findOp, findFunc, binFunc);
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents storage policies.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> storageTypeHistogram(Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return storageTypeHistogramCpu(inodes, sum);
    }
    return storageTypeHistogramCpuWithFind(inodes, find);
  }

  private Map<String, Long> storageTypeHistogramCpu(Collection<INode> inodes, String sum) {
    return versionLoader.storageTypeHistogramCpu(inodes, sum, this);
  }

  private Map<String, Long> storageTypeHistogramCpuWithFind(Collection<INode> inodes, String find) {
    return versionLoader.storageTypeHistogramCpuWithFind(inodes, find, this);
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents access time ranges.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> accessTimeHistogram(
      Collection<INode> inodes, String sum, String find, String timeRange) {
    if (find == null || find.length() == 0) {
      return accessTimeHistogramCpu(inodes, sum, timeRange);
    }
    return accessTimeHistogramCpuWithFind(inodes, find, timeRange);
  }

  private Map<String, Long> accessTimeHistogramCpu(
      Collection<INode> inodes, String sum, String timeRange) {
    return filteringHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        node -> System.currentTimeMillis() - node.getAccessTime(),
        TimeHistogram.getBinsArray(timeRange),
        TimeHistogram.getKeys(timeRange));
  }

  private Map<String, Long> accessTimeHistogramCpuWithFind(
      Collection<INode> inodes, String find, String timeRange) {
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];
    return filteringHistogramWithFind(
        inodes,
        findField,
        findOp,
        getFilterFunctionToLongForINode(findField),
        node -> System.currentTimeMillis() - node.getAccessTime(),
        TimeHistogram.getBinsArray(timeRange),
        TimeHistogram.getKeys(timeRange));
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents modification time
   * ranges.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> modTimeHistogram(
      Collection<INode> inodes, String sum, String find, String timeRange) {
    if (find == null || find.length() == 0) {
      return modTimeHistogramCpu(inodes, sum, timeRange);
    }
    return modTimeHistogramCpuWithFind(inodes, find, timeRange);
  }

  private Map<String, Long> modTimeHistogramCpu(
      Collection<INode> inodes, String sum, String timeRange) {
    return filteringHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        node -> System.currentTimeMillis() - node.getModificationTime(),
        TimeHistogram.getBinsArray(timeRange),
        TimeHistogram.getKeys(timeRange));
  }

  private Map<String, Long> modTimeHistogramCpuWithFind(
      Collection<INode> inodes, String find, String timeRange) {
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];
    return filteringHistogramWithFind(
        inodes,
        findField,
        findOp,
        getFilterFunctionToLongForINode(findField),
        node -> System.currentTimeMillis() - node.getModificationTime(),
        TimeHistogram.getBinsArray(timeRange),
        TimeHistogram.getKeys(timeRange));
  }

  /**
   * Dump collection of INodes to parameter HTTP response.
   *
   * @param inodes the collection to dump
   * @param limit some limit of inodes to show
   * @param resp the HTTP response
   * @throws IOException error in dumping collection
   */
  @Override // QueryEngine
  public void dumpINodePaths(Collection<INode> inodes, Integer limit, HttpServletResponse resp)
      throws IOException {
    LOG.info("Dumping a list of {} INodes to a client.", inodes.size());
    long start = System.currentTimeMillis();
    PrintWriter writer = resp.getWriter();
    try {
      Collection<INode> subCollection;
      if (limit != null && limit < inodes.size()) {
        subCollection = inodes.stream().limit(limit).collect(Collectors.toList());
      } else {
        subCollection = inodes;
      }
      subCollection
          .stream()
          .sorted(Comparator.comparing(INode::getFullPathName))
          .forEach(
              node -> {
                writer.write(node.getFullPathName() + '\n');
                writer.flush();
              });
    } finally {
      IOUtils.closeStream(writer);
      LOG.info("Closed response.");
    }
    long end = System.currentTimeMillis();
    LOG.info("Sending the entire response took {} ms.", (end - start));
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents user names.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> byUserHistogram(Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return byUserHistogramCpu(inodes, sum);
    }
    return byUserHistogramCpuWithFind(inodes, find);
  }

  private Map<String, Long> byUserHistogramCpu(Collection<INode> inodes, String sum) {
    List<String> distinctUsers =
        inodes.parallelStream().map(INode::getUserName).distinct().collect(Collectors.toList());
    Map<String, Long> userToIdMap =
        distinctUsers
            .parallelStream()
            .mapToInt(distinctUsers::indexOf)
            .boxed()
            .collect(Collectors.toMap(distinctUsers::get, value -> (long) value));

    return binMappingHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        node -> userToIdMap.get(node.getUserName()),
        userToIdMap);
  }

  private Map<String, Long> byUserHistogramCpuWithFind(Collection<INode> inodes, String find) {
    List<String> distinctUsers =
        inodes.parallelStream().map(INode::getUserName).distinct().collect(Collectors.toList());
    Map<String, Long> userToIdMap =
        distinctUsers
            .parallelStream()
            .mapToInt(distinctUsers::indexOf)
            .boxed()
            .collect(Collectors.toMap(distinctUsers::get, value -> (long) value));

    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];

    return binMappingHistogramWithFind(
        inodes,
        findOp,
        getFilterFunctionToLongForINode(findField),
        node -> userToIdMap.get(node.getUserName()),
        userToIdMap);
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents group names.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> byGroupHistogram(Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return byGroupHistogramCpu(inodes, sum);
    }
    return byGroupHistogramCpuWithFind(inodes, find);
  }

  private Map<String, Long> byGroupHistogramCpu(Collection<INode> inodes, String sum) {
    List<String> distinctGroups =
        inodes.parallelStream().map(INode::getGroupName).distinct().collect(Collectors.toList());
    Map<String, Long> groupToIdMap =
        distinctGroups
            .parallelStream()
            .mapToInt(distinctGroups::indexOf)
            .boxed()
            .collect(Collectors.toMap(distinctGroups::get, value -> (long) value));

    return binMappingHistogram(
        inodes,
        sum,
        getSumFunctionForINode(sum),
        node -> groupToIdMap.get(node.getGroupName()),
        groupToIdMap);
  }

  private Map<String, Long> byGroupHistogramCpuWithFind(Collection<INode> inodes, String find) {
    List<String> distinctGroups =
        inodes.parallelStream().map(INode::getGroupName).distinct().collect(Collectors.toList());
    Map<String, Long> groupToIdMap =
        distinctGroups
            .parallelStream()
            .mapToInt(distinctGroups::indexOf)
            .boxed()
            .collect(Collectors.toMap(distinctGroups::get, value -> (long) value));

    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];

    return binMappingHistogramWithFind(
        inodes,
        findOp,
        getFilterFunctionToLongForINode(findField),
        node -> groupToIdMap.get(node.getGroupName()),
        groupToIdMap);
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents parent directories.
   *
   * @param inodes the filtered inodes to operate with
   * @param parentDirDepth the depth of the parents to group on
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> parentDirHistogram(
      Collection<INode> inodes, Integer parentDirDepth, String sum, String find) {
    if (find == null || find.length() == 0) {
      return parentDirHistogramCpu(inodes, parentDirDepth, sum);
    }
    return parentDirHistogramCpuWithFind(inodes, parentDirDepth, find);
  }

  private Map<String, Long> parentDirHistogramCpu(
      Collection<INode> inodes, Integer parentDirDepth, String sum) {
    int dirDepth =
        (parentDirDepth == null || parentDirDepth <= 0) ? Integer.MAX_VALUE : parentDirDepth;
    List<String> distinctDirectories =
        inodes
            .parallelStream()
            .map(
                node -> {
                  try {
                    INodeDirectory parent = node.getParent();
                    int topParentDepth = new Path(parent.getFullPathName()).depth();
                    if (topParentDepth < dirDepth) {
                      return "NO_MAPPING";
                    }
                    for (int parentTravs = topParentDepth; parentTravs > dirDepth; parentTravs--) {
                      parent = parent.getParent();
                    }
                    return parent.getFullPathName();
                  } catch (Exception e) {
                    return "NO_MAPPING";
                  }
                })
            .distinct()
            .collect(Collectors.toList());

    final AtomicLong id = new AtomicLong(0L);
    final Map<String, Long> dirToIdMap =
        distinctDirectories
            .parallelStream()
            .collect(Collectors.toMap(dir -> dir, dir -> id.getAndIncrement()));
    if (!dirToIdMap.containsKey("NO_MAPPING")) {
      dirToIdMap.put("NO_MAPPING", id.getAndIncrement());
    }
    final long noMappingId = dirToIdMap.get("NO_MAPPING");

    Map<String, Long> result =
        binMappingHistogram(
            inodes,
            sum,
            getSumFunctionForINode(sum),
            node -> {
              try {
                INodeDirectory parent = node.getParent();
                int topParentDepth = new Path(parent.getFullPathName()).depth();
                if (topParentDepth < dirDepth) {
                  return noMappingId;
                }
                for (int parentTravs = topParentDepth; parentTravs > dirDepth; parentTravs--) {
                  parent = parent.getParent();
                }
                Long index = dirToIdMap.get(parent.getFullPathName());
                return index != null ? index : noMappingId;
              } catch (Throwable e) {
                return noMappingId;
              }
            },
            dirToIdMap);
    result.remove("NO_MAPPING");
    return result;
  }

  private Map<String, Long> parentDirHistogramCpuWithFind(
      Collection<INode> inodes, Integer parentDirDepth, String find) {
    int dirDepth = (parentDirDepth != null) ? parentDirDepth : 0;
    List<String> distinctDirectories =
        inodes
            .parallelStream()
            .map(
                node -> {
                  try {
                    INodeDirectory parent = node.getParent();
                    int topParentDepth = new Path(parent.getFullPathName()).depth();
                    if (topParentDepth < dirDepth) {
                      return "NO_MAPPING";
                    }
                    for (int parentTravs = topParentDepth; parentTravs > dirDepth; parentTravs--) {
                      parent = parent.getParent();
                    }
                    return parent.getFullPathName();
                  } catch (Exception e) {
                    return "NO_MAPPING";
                  }
                })
            .distinct()
            .collect(Collectors.toList());

    final AtomicLong id = new AtomicLong(0L);
    Map<String, Long> dirToIdMap =
        distinctDirectories
            .parallelStream()
            .collect(Collectors.toMap(dir -> dir, dir -> id.getAndIncrement()));
    if (!dirToIdMap.containsKey("NO_MAPPING")) {
      dirToIdMap.put("NO_MAPPING", id.getAndIncrement());
    }
    final long noMappingId = dirToIdMap.get("NO_MAPPING");
    String[] finds = find.split(":");
    String findOp = finds[0];
    String findField = finds[1];

    Map<String, Long> result =
        binMappingHistogramWithFind(
            inodes,
            findOp,
            getFilterFunctionToLongForINode(findField),
            node -> {
              try {
                INodeDirectory parent = node.getParent();
                int topParentDepth = new Path(parent.getFullPathName()).depth();
                if (topParentDepth < dirDepth) {
                  return noMappingId;
                }
                for (int parentTravs = topParentDepth; parentTravs > dirDepth; parentTravs--) {
                  parent = parent.getParent();
                }
                Long index = dirToIdMap.get(parent.getFullPathName());
                return index != null ? index : noMappingId;
              } catch (Throwable e) {
                return noMappingId;
              }
            },
            dirToIdMap);
    result.remove("NO_MAPPING");
    return result;
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents a file type extension.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @param find optional; a find operation to perform; overrides sum
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> fileTypeHistogram(Collection<INode> inodes, String sum, String find) {
    if (find == null || find.length() == 0) {
      return fileTypeHistogramCpu(inodes, sum);
    }
    return fileTypeHistogramCpu(inodes, sum);
  }

  private Map<String, Long> fileTypeHistogramCpu(Collection<INode> inodes, String sum) {
    List<String> fileTypes = FileTypeHistogram.keys;

    Map<String, Long> typeToIdMap =
        fileTypes
            .parallelStream()
            .mapToInt(fileTypes::indexOf)
            .boxed()
            .collect(Collectors.toMap(fileTypes::get, value -> (long) value));

    Map<String, Long> histogram =
        binMappingHistogram(
            inodes,
            sum,
            getSumFunctionForINode(sum),
            node -> typeToIdMap.get(FileTypeHistogram.determineType(node.getLocalName())),
            typeToIdMap);

    return removeKeysOnConditional(histogram, "gt:0");
  }

  /**
   * Creates a histogram representation of INodes where the X-axis represents a directory quota.
   *
   * @param inodes the filtered inodes to operate with
   * @param sum the Y-axis type
   * @return a map representing bins as Strings and the sum/finds as Longs
   */
  @Override // QueryEngine
  public Map<String, Long> dirQuotaHistogram(Collection<INode> inodes, String sum) {
    return dirQuotaHistogramCpu(inodes, sum);
  }

  private Map<String, Long> dirQuotaHistogramCpu(Collection<INode> inodes, String sum) {
    List<String> distinctDirectories =
        inodes.parallelStream().map(INode::getFullPathName).distinct().collect(Collectors.toList());

    final AtomicLong id = new AtomicLong(0L);
    Map<String, Long> dirToIdMap =
        distinctDirectories
            .parallelStream()
            .collect(Collectors.toMap(dir -> dir, dir -> id.getAndIncrement()));

    Map<String, Long> histogram =
        binMappingHistogram(
            inodes,
            sum,
            getSumFunctionForINode(sum),
            node -> dirToIdMap.get(node.getFullPathName()),
            dirToIdMap);

    return removeKeysOnConditional(histogram, "gte:0");
  }

  /**
   * Creates histogram with only entries that satisfy the conditional String. Conditional String ex:
   * 'gte:1000' should create a histogram where all entries have values greater than or equal to
   * 1000L. NOTE: Modifies the parameter histogram.
   *
   * @param histogram data points of histogram
   * @param histogramConditionsStr conditional string to filter out the given histogram
   * @return filtered histogram as per the given conditional string
   */
  @Override // QueryEngine
  public Map<String, Long> removeKeysOnConditional(
      Map<String, Long> histogram, String histogramConditionsStr) {
    long s1 = System.currentTimeMillis();
    int originalHistSize = histogram.size();

    List<Function<Long, Boolean>> comparisons = createComparisons(histogramConditionsStr);
    Set<String> keys = new HashSet<>();
    for (Map.Entry<String, Long> entry : histogram.entrySet()) {
      boolean columnCheck = check(comparisons, entry.getValue());
      if (!columnCheck) {
        keys.add(entry.getKey());
      }
    }
    keys.forEach(histogram::remove);

    long e1 = System.currentTimeMillis();
    LOG.info(
        "Removing {} keys from histogram of size {} using conditional String:'{}', took: {} ms.",
        keys.size(),
        originalHistSize,
        histogramConditionsStr,
        (e1 - s1));

    return histogram;
  }

  /**
   * Result is a histogram with only entries that satisfy the conditional String. Conditional String
   * ex: 'gte:1000' should create a histogram where all entries have values greater than or equal to
   * 1000L.
   *
   * <p>NOTE: Modifies the parameter histogram.
   *
   * @param histogram data points of histogram
   * @param histogramConditionsStr conditional string to filter out the given histogram
   * @return filtered histogram as per the given conditional string
   */
  @Override // QueryEngine
  public Map<String, List<Long>> removeKeysOnConditional2(
      Map<String, List<Long>> histogram, String histogramConditionsStr) {
    long s1 = System.currentTimeMillis();
    int originalHistSize = histogram.size();

    List<Function<List<Long>, Boolean>> comparisons =
        createIndexedComparisons(histogramConditionsStr);
    Set<String> keys = new HashSet<>();
    for (Map.Entry<String, List<Long>> entry : histogram.entrySet()) {
      boolean columnCheck = check(comparisons, entry.getValue());
      if (!columnCheck) {
        keys.add(entry.getKey());
      }
    }
    keys.forEach(histogram::remove);

    long e1 = System.currentTimeMillis();
    LOG.info(
        "Removing {} keys from histogram2 of size {} using conditional String:'{}', took: {} ms.",
        keys.size(),
        originalHistSize,
        histogramConditionsStr,
        (e1 - s1));

    return histogram;
  }

  /**
   * Splits a conditional String and ANDs them together.
   *
   * @param conditionsStr conditional string seperated by semicolons; conditions by colons
   * @return a list of functions that represent the conditional
   */
  @Override // QueryEngine
  public List<Function<Long, Boolean>> createComparisons(String conditionsStr) {
    String[] conditionsArray = conditionsStr.split(";");
    String[][] conditionTuplets = new String[conditionsArray.length][2];

    List<Function<Long, Boolean>> comparisons = new ArrayList<>(conditionTuplets.length);
    for (int i = 0; i < conditionTuplets.length; i++) {
      String tuplet = conditionsArray[i];
      conditionTuplets[i] = tuplet.split(":");
    }

    // Create comparisons.
    for (String[] condition : conditionTuplets) {
      Function<Long, Boolean> longFunction =
          getFilterFunctionForLong(Long.parseLong(condition[1]), condition[0]);
      comparisons.add(longFunction);
    }

    return comparisons;
  }

  /**
   * Check the parameter `value` against a series of checks.
   *
   * @param comparisons the Long to Boolean comparison functions
   * @param value the Long value to compare against
   * @return true if checks passed; false otherwise
   */
  @Override // QueryEngine
  public boolean check(List<Function<Long, Boolean>> comparisons, long value) {
    boolean check = true;
    for (Function<Long, Boolean> comparison : comparisons) {
      boolean compareResult = comparison.apply(value);
      if (!compareResult) {
        check = false;
        break;
      }
    }
    return check;
  }

  private boolean check(List<Function<List<Long>, Boolean>> comparisons, List<Long> value) {
    boolean check = true;
    for (Function<List<Long>, Boolean> comparison : comparisons) {
      boolean compareResult = comparison.apply(value);
      if (!compareResult) {
        check = false;
        break;
      }
    }
    return check;
  }

  private List<Function<List<Long>, Boolean>> createIndexedComparisons(String conditionsStr) {
    String[] conditionsArray = conditionsStr.split(",");
    String[][] conditionTuplets = new String[conditionsArray.length][3];

    List<Function<List<Long>, Boolean>> comparisons = new ArrayList<>(conditionTuplets.length);
    for (int i = 0; i < conditionTuplets.length; i++) {
      String triplet = conditionsArray[i];
      conditionTuplets[i] = triplet.split(":");
    }

    // Create comparisons.
    for (String[] condition : conditionTuplets) {
      Function<Long, Boolean> longFunction =
          getFilterFunctionForLong(Long.parseLong(condition[2]), condition[1]);
      Function<List<Long>, Boolean> indexedLongFunction =
          (list) -> longFunction.apply(list.get(Integer.parseInt(condition[0])));
      comparisons.add(indexedLongFunction);
    }

    return comparisons;
  }

  /**
   * Performs an SQL query against this query engine.
   *
   * @param req the http request
   * @param res the http response
   * @return The same HttpServletResponse as param.
   */
  public HttpServletResponse sql(HttpServletRequest req, HttpServletResponse res) {
    Map<String, Attribute<INode, ?>> attributes = new HashMap<>();
    attributes.put("id", id);
    attributes.put("accessTime", accessTime);
    attributes.put("modTime", modTime);
    attributes.put("fileSize", fileSize);
    attributes.put("diskspaceConsumed", diskspaceConsumed);
    attributes.put("memoryConsumed", memoryConsumed);
    attributes.put("fileReplica", fileReplica);
    attributes.put("numBlocks", numBlocks);
    attributes.put("numReplicas", numReplicas);
    attributes.put("dirNumChildren", dirNumChildren);
    attributes.put("dirSubTreeSize", dirSubTreeSize);
    attributes.put("dirSubTreeNumFiles", dirSubTreeNumFiles);
    attributes.put("dirSubTreeNumDirs", dirSubTreeNumDirs);
    attributes.put("storageType", storageType);
    attributes.put("depth", depth);
    attributes.put("permission", permission);
    attributes.put("name", name);
    attributes.put("path", path);
    attributes.put("user", user);
    attributes.put("group", group);
    attributes.put("modDate", modDate);
    attributes.put("accessDate", accessDate);
    attributes.put("isUnderConstruction", isUnderConstruction);
    attributes.put("isWithSnapshot", isWithSnapshot);
    attributes.put("hasAcl", hasAcl);
    attributes.put("hasQuota", hasQuota);

    SQLParser<INode> parser = SQLParser.forPojoWithAttributes(INode.class, attributes);

    long count = 0;
    try (PrintWriter out = res.getWriter()) {
      res.setHeader("Access-Control-Allow-Origin", "*");
      res.setHeader("Content-Type", "text/plain");
      String sql = req.getParameter("sqlStatement");
      ResultSet<INode> results = parser.retrieve(indexedFiles, sql);
      for (INode inode : results) {
        out.println(inode.getFullPathName());
        count++;
      }
      res.setStatus(HttpStatus.SC_OK);
    } catch (IOException e) {
      LOG.warn("SQL statement failed.", e);
    }
    LOG.info("SQL statement produced result of: {} inodes.", count);

    return res;
  }
}
