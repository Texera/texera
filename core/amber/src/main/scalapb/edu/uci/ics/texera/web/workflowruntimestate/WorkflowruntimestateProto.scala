// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package edu.uci.ics.texera.web.workflowruntimestate

object WorkflowruntimestateProto extends _root_.scalapb.GeneratedFileObject {
  lazy val dependencies: Seq[_root_.scalapb.GeneratedFileObject] = Seq(
    edu.uci.ics.amber.engine.architecture.worker.controlcommands.ControlcommandsProto,
    edu.uci.ics.amber.engine.architecture.worker.controlreturns.ControlreturnsProto,
    edu.uci.ics.amber.engine.architecture.worker.statistics.StatisticsProto,
    edu.uci.ics.amber.engine.common.virtualidentity.VirtualidentityProto,
    com.google.protobuf.timestamp.TimestampProto,
    scalapb.options.ScalapbProto
  )
  lazy val messagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] =
    Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]](
      edu.uci.ics.texera.web.workflowruntimestate.BreakpointFault,
      edu.uci.ics.texera.web.workflowruntimestate.OperatorBreakpoints,
      edu.uci.ics.texera.web.workflowruntimestate.ExecutionBreakpointStore,
      edu.uci.ics.texera.web.workflowruntimestate.EvaluatedValueList,
      edu.uci.ics.texera.web.workflowruntimestate.OperatorConsole,
      edu.uci.ics.texera.web.workflowruntimestate.ExecutionConsoleStore,
      edu.uci.ics.texera.web.workflowruntimestate.OperatorWorkerMapping,
      edu.uci.ics.texera.web.workflowruntimestate.OperatorStatistics,
      edu.uci.ics.texera.web.workflowruntimestate.OperatorMetrics,
      edu.uci.ics.texera.web.workflowruntimestate.ExecutionStatsStore,
      edu.uci.ics.texera.web.workflowruntimestate.WorkflowFatalError,
      edu.uci.ics.texera.web.workflowruntimestate.ExecutionMetadataStore
    )
  private lazy val ProtoBytes: _root_.scala.Array[Byte] =
      scalapb.Encoding.fromBase64(scala.collection.immutable.Seq(
  """Ci1lZHUvdWNpL2ljcy90ZXhlcmEvd29ya2Zsb3dydW50aW1lc3RhdGUucHJvdG8SFmVkdS51Y2kuaWNzLnRleGVyYS53ZWIaQ
  mVkdS91Y2kvaWNzL2FtYmVyL2VuZ2luZS9hcmNoaXRlY3R1cmUvd29ya2VyL2NvbnRyb2xjb21tYW5kcy5wcm90bxpBZWR1L3Vja
  S9pY3MvYW1iZXIvZW5naW5lL2FyY2hpdGVjdHVyZS93b3JrZXIvY29udHJvbHJldHVybnMucHJvdG8aPWVkdS91Y2kvaWNzL2FtY
  mVyL2VuZ2luZS9hcmNoaXRlY3R1cmUvd29ya2VyL3N0YXRpc3RpY3MucHJvdG8aNWVkdS91Y2kvaWNzL2FtYmVyL2VuZ2luZS9jb
  21tb24vdmlydHVhbGlkZW50aXR5LnByb3RvGh9nb29nbGUvcHJvdG9idWYvdGltZXN0YW1wLnByb3RvGhVzY2FsYXBiL3NjYWxhc
  GIucHJvdG8iqwIKD0JyZWFrcG9pbnRGYXVsdBIwCgt3b3JrZXJfbmFtZRgBIAEoCUIP4j8MEgp3b3JrZXJOYW1lUgp3b3JrZXJOY
  W1lEm8KDWZhdWx0ZWRfdHVwbGUYAiABKAsyNy5lZHUudWNpLmljcy50ZXhlcmEud2ViLkJyZWFrcG9pbnRGYXVsdC5CcmVha3Bva
  W50VHVwbGVCEeI/DhIMZmF1bHRlZFR1cGxlUgxmYXVsdGVkVHVwbGUadQoPQnJlYWtwb2ludFR1cGxlEhcKAmlkGAEgASgDQgfiP
  wQSAmlkUgJpZBInCghpc19pbnB1dBgCIAEoCEIM4j8JEgdpc0lucHV0Ugdpc0lucHV0EiAKBXR1cGxlGAMgAygJQgriPwcSBXR1c
  GxlUgV0dXBsZSKRAQoTT3BlcmF0b3JCcmVha3BvaW50cxJ6ChZ1bnJlc29sdmVkX2JyZWFrcG9pbnRzGAEgAygLMicuZWR1LnVja
  S5pY3MudGV4ZXJhLndlYi5CcmVha3BvaW50RmF1bHRCGuI/FxIVdW5yZXNvbHZlZEJyZWFrcG9pbnRzUhV1bnJlc29sdmVkQnJlY
  Wtwb2ludHMimwIKGEV4ZWN1dGlvbkJyZWFrcG9pbnRTdG9yZRJ6Cg1vcGVyYXRvcl9pbmZvGAEgAygLMkIuZWR1LnVjaS5pY3Mud
  GV4ZXJhLndlYi5FeGVjdXRpb25CcmVha3BvaW50U3RvcmUuT3BlcmF0b3JJbmZvRW50cnlCEeI/DhIMb3BlcmF0b3JJbmZvUgxvc
  GVyYXRvckluZm8aggEKEU9wZXJhdG9ySW5mb0VudHJ5EhoKA2tleRgBIAEoCUII4j8FEgNrZXlSA2tleRJNCgV2YWx1ZRgCIAEoC
  zIrLmVkdS51Y2kuaWNzLnRleGVyYS53ZWIuT3BlcmF0b3JCcmVha3BvaW50c0IK4j8HEgV2YWx1ZVIFdmFsdWU6AjgBIncKEkV2Y
  Wx1YXRlZFZhbHVlTGlzdBJhCgZ2YWx1ZXMYASADKAsyPC5lZHUudWNpLmljcy5hbWJlci5lbmdpbmUuYXJjaGl0ZWN0dXJlLndvc
  mtlci5FdmFsdWF0ZWRWYWx1ZUIL4j8IEgZ2YWx1ZXNSBnZhbHVlcyKsAwoPT3BlcmF0b3JDb25zb2xlEn0KEGNvbnNvbGVfbWVzc
  2FnZXMYASADKAsyPC5lZHUudWNpLmljcy5hbWJlci5lbmdpbmUuYXJjaGl0ZWN0dXJlLndvcmtlci5Db25zb2xlTWVzc2FnZUIU4
  j8REg9jb25zb2xlTWVzc2FnZXNSD2NvbnNvbGVNZXNzYWdlcxKOAQoVZXZhbHVhdGVfZXhwcl9yZXN1bHRzGAIgAygLMkAuZWR1L
  nVjaS5pY3MudGV4ZXJhLndlYi5PcGVyYXRvckNvbnNvbGUuRXZhbHVhdGVFeHByUmVzdWx0c0VudHJ5QhjiPxUSE2V2YWx1YXRlR
  XhwclJlc3VsdHNSE2V2YWx1YXRlRXhwclJlc3VsdHMaiAEKGEV2YWx1YXRlRXhwclJlc3VsdHNFbnRyeRIaCgNrZXkYASABKAlCC
  OI/BRIDa2V5UgNrZXkSTAoFdmFsdWUYAiABKAsyKi5lZHUudWNpLmljcy50ZXhlcmEud2ViLkV2YWx1YXRlZFZhbHVlTGlzdEIK4
  j8HEgV2YWx1ZVIFdmFsdWU6AjgBIqECChVFeGVjdXRpb25Db25zb2xlU3RvcmUSgwEKEG9wZXJhdG9yX2NvbnNvbGUYASADKAsyQ
  i5lZHUudWNpLmljcy50ZXhlcmEud2ViLkV4ZWN1dGlvbkNvbnNvbGVTdG9yZS5PcGVyYXRvckNvbnNvbGVFbnRyeUIU4j8REg9vc
  GVyYXRvckNvbnNvbGVSD29wZXJhdG9yQ29uc29sZRqBAQoUT3BlcmF0b3JDb25zb2xlRW50cnkSGgoDa2V5GAEgASgJQgjiPwUSA
  2tleVIDa2V5EkkKBXZhbHVlGAIgASgLMicuZWR1LnVjaS5pY3MudGV4ZXJhLndlYi5PcGVyYXRvckNvbnNvbGVCCuI/BxIFdmFsd
  WVSBXZhbHVlOgI4ASJ2ChVPcGVyYXRvcldvcmtlck1hcHBpbmcSLwoKb3BlcmF0b3JJZBgBIAEoCUIP4j8MEgpvcGVyYXRvcklkU
  gpvcGVyYXRvcklkEiwKCXdvcmtlcklkcxgCIAMoCUIO4j8LEgl3b3JrZXJJZHNSCXdvcmtlcklkcyKCBAoST3BlcmF0b3JTdGF0a
  XN0aWNzEnUKC2lucHV0X2NvdW50GAEgAygLMkMuZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS53b3JrZXIuU
  G9ydFR1cGxlQ291bnRNYXBwaW5nQg/iPwwSCmlucHV0Q291bnRSCmlucHV0Q291bnQSeAoMb3V0cHV0X2NvdW50GAIgAygLMkMuZ
  WR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS53b3JrZXIuUG9ydFR1cGxlQ291bnRNYXBwaW5nQhDiPw0SC291d
  HB1dENvdW50UgtvdXRwdXRDb3VudBIwCgtudW1fd29ya2VycxgDIAEoBUIP4j8MEgpudW1Xb3JrZXJzUgpudW1Xb3JrZXJzEkkKF
  GRhdGFfcHJvY2Vzc2luZ190aW1lGAQgASgDQhfiPxQSEmRhdGFQcm9jZXNzaW5nVGltZVISZGF0YVByb2Nlc3NpbmdUaW1lElIKF
  2NvbnRyb2xfcHJvY2Vzc2luZ190aW1lGAUgASgDQhriPxcSFWNvbnRyb2xQcm9jZXNzaW5nVGltZVIVY29udHJvbFByb2Nlc3Npb
  mdUaW1lEioKCWlkbGVfdGltZRgGIAEoA0IN4j8KEghpZGxlVGltZVIIaWRsZVRpbWUi+QEKD09wZXJhdG9yTWV0cmljcxJtCg5vc
  GVyYXRvcl9zdGF0ZRgBIAEoDjIvLmVkdS51Y2kuaWNzLnRleGVyYS53ZWIuV29ya2Zsb3dBZ2dyZWdhdGVkU3RhdGVCFeI/EhINb
  3BlcmF0b3JTdGF0ZfABAVINb3BlcmF0b3JTdGF0ZRJ3ChNvcGVyYXRvcl9zdGF0aXN0aWNzGAIgASgLMiouZWR1LnVjaS5pY3Mud
  GV4ZXJhLndlYi5PcGVyYXRvclN0YXRpc3RpY3NCGuI/FxISb3BlcmF0b3JTdGF0aXN0aWNz8AEBUhJvcGVyYXRvclN0YXRpc3RpY
  3MihAQKE0V4ZWN1dGlvblN0YXRzU3RvcmUSOwoOc3RhcnRUaW1lU3RhbXAYASABKANCE+I/EBIOc3RhcnRUaW1lU3RhbXBSDnN0Y
  XJ0VGltZVN0YW1wEjUKDGVuZFRpbWVTdGFtcBgCIAEoA0IR4j8OEgxlbmRUaW1lU3RhbXBSDGVuZFRpbWVTdGFtcBJ1Cg1vcGVyY
  XRvcl9pbmZvGAMgAygLMj0uZWR1LnVjaS5pY3MudGV4ZXJhLndlYi5FeGVjdXRpb25TdGF0c1N0b3JlLk9wZXJhdG9ySW5mb0Vud
  HJ5QhHiPw4SDG9wZXJhdG9ySW5mb1IMb3BlcmF0b3JJbmZvEoEBChdvcGVyYXRvcl93b3JrZXJfbWFwcGluZxgEIAMoCzItLmVkd
  S51Y2kuaWNzLnRleGVyYS53ZWIuT3BlcmF0b3JXb3JrZXJNYXBwaW5nQhriPxcSFW9wZXJhdG9yV29ya2VyTWFwcGluZ1IVb3Blc
  mF0b3JXb3JrZXJNYXBwaW5nGn4KEU9wZXJhdG9ySW5mb0VudHJ5EhoKA2tleRgBIAEoCUII4j8FEgNrZXlSA2tleRJJCgV2YWx1Z
  RgCIAEoCzInLmVkdS51Y2kuaWNzLnRleGVyYS53ZWIuT3BlcmF0b3JNZXRyaWNzQgriPwcSBXZhbHVlUgV2YWx1ZToCOAEi1AIKE
  ldvcmtmbG93RmF0YWxFcnJvchJFCgR0eXBlGAEgASgOMiYuZWR1LnVjaS5pY3MudGV4ZXJhLndlYi5GYXRhbEVycm9yVHlwZUIJ4
  j8GEgR0eXBlUgR0eXBlEksKCXRpbWVzdGFtcBgCIAEoCzIaLmdvb2dsZS5wcm90b2J1Zi5UaW1lc3RhbXBCEeI/DhIJdGltZXN0Y
  W1w8AEBUgl0aW1lc3RhbXASJgoHbWVzc2FnZRgDIAEoCUIM4j8JEgdtZXNzYWdlUgdtZXNzYWdlEiYKB2RldGFpbHMYBCABKAlCD
  OI/CRIHZGV0YWlsc1IHZGV0YWlscxIvCgpvcGVyYXRvcklkGAUgASgJQg/iPwwSCm9wZXJhdG9ySWRSCm9wZXJhdG9ySWQSKQoId
  29ya2VySWQYBiABKAlCDeI/ChIId29ya2VySWRSCHdvcmtlcklkIu8CChZFeGVjdXRpb25NZXRhZGF0YVN0b3JlElEKBXN0YXRlG
  AEgASgOMi8uZWR1LnVjaS5pY3MudGV4ZXJhLndlYi5Xb3JrZmxvd0FnZ3JlZ2F0ZWRTdGF0ZUIK4j8HEgVzdGF0ZVIFc3RhdGUSX
  woMZmF0YWxfZXJyb3JzGAIgAygLMiouZWR1LnVjaS5pY3MudGV4ZXJhLndlYi5Xb3JrZmxvd0ZhdGFsRXJyb3JCEOI/DRILZmF0Y
  WxFcnJvcnNSC2ZhdGFsRXJyb3JzEmkKC2V4ZWN1dGlvbklkGAMgASgLMjIuZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmNvbW1vb
  i5FeGVjdXRpb25JZGVudGl0eUIT4j8QEgtleGVjdXRpb25JZPABAVILZXhlY3V0aW9uSWQSNgoNaXNfcmVjb3ZlcmluZxgEIAEoC
  EIR4j8OEgxpc1JlY292ZXJpbmdSDGlzUmVjb3ZlcmluZyo+Cg5GYXRhbEVycm9yVHlwZRIVChFDT01QSUxBVElPTl9FUlJPUhAAE
  hUKEUVYRUNVVElPTl9GQUlMVVJFEAEqnwEKF1dvcmtmbG93QWdncmVnYXRlZFN0YXRlEhEKDVVOSU5JVElBTElaRUQQABIJCgVSR
  UFEWRABEgsKB1JVTk5JTkcQAhILCgdQQVVTSU5HEAMSCgoGUEFVU0VEEAQSDAoIUkVTVU1JTkcQBRINCglDT01QTEVURUQQBhIKC
  gZGQUlMRUQQBxILCgdVTktOT1dOEAgSCgoGS0lMTEVEEAlCCeI/BkgAWAB4AGIGcHJvdG8z"""
      ).mkString)
  lazy val scalaDescriptor: _root_.scalapb.descriptors.FileDescriptor = {
    val scalaProto = com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(ProtoBytes)
    _root_.scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, dependencies.map(_.scalaDescriptor))
  }
  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val javaProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(ProtoBytes)
    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(javaProto, _root_.scala.Array(
      edu.uci.ics.amber.engine.architecture.worker.controlcommands.ControlcommandsProto.javaDescriptor,
      edu.uci.ics.amber.engine.architecture.worker.controlreturns.ControlreturnsProto.javaDescriptor,
      edu.uci.ics.amber.engine.architecture.worker.statistics.StatisticsProto.javaDescriptor,
      edu.uci.ics.amber.engine.common.virtualidentity.VirtualidentityProto.javaDescriptor,
      com.google.protobuf.timestamp.TimestampProto.javaDescriptor,
      scalapb.options.ScalapbProto.javaDescriptor
    ))
  }
  @deprecated("Use javaDescriptor instead. In a future version this will refer to scalaDescriptor.", "ScalaPB 0.5.47")
  def descriptor: com.google.protobuf.Descriptors.FileDescriptor = javaDescriptor
}