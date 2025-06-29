/*
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

package edu.uci.ics.texera.web.service

import edu.uci.ics.amber.core.workflow.PhysicalPlan
import edu.uci.ics.amber.engine.architecture.rpc.controlcommands.{
  ModifyLogicRequest,
  PropagateEmbeddedControlMessageRequest
}
import edu.uci.ics.amber.engine.architecture.scheduling.{Region, WorkflowExecutionCoordinator}
import edu.uci.ics.amber.core.virtualidentity.PhysicalOpIdentity
import org.jgrapht.alg.connectivity.ConnectivityInspector

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.SetHasAsScala

object FriesReconfigurationAlgorithm {

  private def getOneToManyOperators(region: Region): Set[PhysicalOpIdentity] = {
    region.getOperators.filter(op => op.isOneToManyOp).map(op => op.id)
  }

  def scheduleReconfigurations(
      workflowExecutionCoordinator: WorkflowExecutionCoordinator,
      reconfiguration: ModifyLogicRequest,
      epochMarkerId: String
  ): Set[PropagateEmbeddedControlMessageRequest] = {
    // independently schedule reconfigurations for each region:
    workflowExecutionCoordinator.getExecutingRegions
      .flatMap(region => computeMCS(region, reconfiguration, epochMarkerId))
  }

  private def computeMCS(
      region: Region,
      reconfiguration: ModifyLogicRequest,
      epochMarkerId: String
  ): List[PropagateEmbeddedControlMessageRequest] = {

    // add all reconfiguration operators to M
    val reconfigOps = reconfiguration.updateRequest.map(req => req.targetOpId).toSet
    val M = mutable.Set.empty ++ reconfigOps

    // for each one-to-many operator, add it to M if its downstream has a reconfiguration operator
    val oneToManyOperators = getOneToManyOperators(region)
    oneToManyOperators.foreach(oneToManyOp => {
      val intersection = region.dag.getDescendants(oneToManyOp).asScala.intersect(reconfigOps)
      if (intersection.nonEmpty) {
        M += oneToManyOp
      }
    })

    // compute MCS based on M
    var forwardVertices: Set[PhysicalOpIdentity] = Set()
    var backwardVertices: Set[PhysicalOpIdentity] = Set()

    val topologicalOps = region.topologicalIterator().toList
    val reverseTopologicalOps = topologicalOps.reverse

    topologicalOps.foreach(opId => {
      val op = region.getOperator(opId)
      val parents = op.inputPorts.flatMap(_._2._2).map(_.fromOpId)
      val fromParent: Boolean = parents.exists(p => forwardVertices.contains(p))
      if (M.contains(opId) || fromParent) {
        forwardVertices += opId
      }
    })

    reverseTopologicalOps.foreach(opId => {
      val op = region.getOperator(opId)
      val children = op.outputPorts.flatMap(_._2._2).map(_.toOpId)
      val fromChildren: Boolean = children.exists(p => backwardVertices.contains(p))
      if (M.contains(opId) || fromChildren) {
        backwardVertices += opId
      }
    })

    val resultMCSOpIds = forwardVertices.intersect(backwardVertices)
    val newLinks =
      region.getLinks.filter(link =>
        resultMCSOpIds.contains(link.fromOpId) && resultMCSOpIds.contains(link.toOpId)
      )
    val mcsPlan = PhysicalPlan(resultMCSOpIds.map(opId => region.getOperator(opId)), newLinks)

    // find the MCS components,
    // for each component, send an epoch marker to each of its source operators
    val epochMarkers = new ArrayBuffer[PropagateEmbeddedControlMessageRequest]()

    val connectedSets = new ConnectivityInspector(mcsPlan.dag).connectedSets()
    connectedSets.forEach(component => {
      val componentSet = component.asScala.toSet
      val componentPlan = mcsPlan.getSubPlan(componentSet)

      // generate the reconfiguration command for this component
      //      val reconfigCommands =
      //        reconfiguration.updateRequest
      //          .filter(req => component.contains(req.targetOpId))
      //      val reconfigTargets = reconfigCommands.map(_.targetOpId)
      //
      //      // find the source operators of the component
      //      val sources = componentSet.intersect(mcsPlan.getSourceOperatorIds)
      //      epochMarkers += PropagateEmbeddedControlMessageRequest(
      //        sources.toSeq,
      //        EmbeddedControlMessageIdentity(epochMarkerId),
      //        ALL_ALIGNMENT,
      //        componentPlan.operators.map(_.id).toSeq,
      //        reconfigTargets,
      //        ModifyLogicRequest(reconfigCommands),
      //        METHOD_MODIFY_LOGIC.getBareMethodName
      //      )
    })
    epochMarkers.toList
  }

}
