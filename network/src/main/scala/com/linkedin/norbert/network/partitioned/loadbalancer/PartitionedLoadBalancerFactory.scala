/*
 * Copyright 2009-2010 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.linkedin.norbert
package network
package partitioned
package loadbalancer


import _root_.scala.Predef._
import cluster.{InvalidClusterException, Node}
import common.Endpoint

/**
 * A <code>PartitionedLoadBalancer</code> handles calculating the next <code>Node</code> a message should be routed to
 * based on a PartitionedId.
 */
trait PartitionedLoadBalancer[PartitionedId] {
  /**
   * Returns the next <code>Node</code> a message should be routed to based on the PartitionId provided.
   *
   * @param id the id to be used to calculate partitioning information.
   *
   * @return the <code>Node</code> to route the next message to
   */
  def nextNode(id: PartitionedId, capability: Option[Long] = None, persistentCapability: Option[Long] = None): Option[Node]

  /**
   * Returns a list of nodes representing one replica of the cluster, this is used by the PartitionedNetworkClient to handle
   * broadcast to one replica
   *
   * @return the <code>Nodes</code> to broadcast the next message to a replica to
   */
  def nodesForOneReplica(id: PartitionedId, capability: Option[Long] = None, persistentCapability: Option[Long] = None): Map[Node, Set[Int]]

  /**
   * Returns a list of nodes representing all replica for this particular partitionedId
   * @return the <code>Nodes</code> to multicast the message to
   */
  def nodesForPartitionedId(id: PartitionedId, capability: Option[Long] = None, persistentCapability: Option[Long] = None): Set[Node]

  /**
   * Calculates a mapping of nodes to partitions for broadcasting a partitioned request. Optionally uses a partitioned
   * id for consistent hashing purposes
   *
   * @return the <code>Nodes</code> to broadcast the next message to a replica to
   */
  def nodesForPartitions(id: PartitionedId, partitions: Set[Int], capability: Option[Long] = None, persistentCapability: Option[Long] = None): Map[Node, Set[Int]]

  /**
   * Calculates a mapping of nodes to partitions to ensure ids belong to the same partition will be scatter to the same node
   * @param id
   * @param capability
   * @param persistentCapability
   * @return
   */
  def nodesForPartitionedIds(ids: Set[PartitionedId], capability: Option[Long] = None, persistentCapability: Option[Long] = None): Map[Node,  Set[PartitionedId]]  =
  {
    ids.foldLeft(Map[Node, Set[PartitionedId]]().withDefaultValue(Set())) { (map, id) =>
      val node = nextNode(id, capability, persistentCapability).getOrElse(throw new NoNodesAvailableException("Unable to satisfy request, no node available for id %s".format(id)))
      map.updated(node, map(node) + id)
    }
  }

  /**
   * Calculates a mapping of nodes to partitions. The nodes should be selected from the given number of replicas.
   * Initial implementation is delegating request to maximum degree of fan-out. Implementation should override this
   * default implementation.
   *
   * @param ids set of partition ids.
   * @param numberOfReplicas number of replica
   * @param capability
   * @param persistentCapability
   * @return a map from node to partition
   */
  def nodesForPartitionsIdsInNReplicas(ids: Set[PartitionedId], numberOfReplicas: Int, capability: Option[Long] = None,
                                       persistentCapability: Option[Long] = None): Map[Node, Set[PartitionedId]] =
  {
    // Default implementation is just select nodes from all replicas.
    nodesForPartitionedIds(ids, capability, persistentCapability)
  }
}

/**
 * A factory which can generate <code>PartitionedLoadBalancer</code>s.
 */
trait PartitionedLoadBalancerFactory[PartitionedId] {
  /**
   * Create a new load balancer instance based on the currently available <code>Node</code>s.
   *
   * @param nodes the currently available <code>Node</code>s in the cluster
   *
   * @return a new <code>PartitionedLoadBalancer</code> instance
   * @throws InvalidClusterException thrown to indicate that the current cluster topology is invalid in some way and
   * it is impossible to create a <code>LoadBalancer</code>
   */
  @throws(classOf[InvalidClusterException])
  def newLoadBalancer(nodes: Set[Endpoint]): PartitionedLoadBalancer[PartitionedId]

  def getNumPartitions(endpoints: Set[Endpoint]): Int
}

/**
 * A component which provides a <code>PartitionedLoadBalancerFactory</code>.
 */
trait PartitionedLoadBalancerFactoryComponent[PartitionedId] {
  val loadBalancerFactory: PartitionedLoadBalancerFactory[PartitionedId]
}
