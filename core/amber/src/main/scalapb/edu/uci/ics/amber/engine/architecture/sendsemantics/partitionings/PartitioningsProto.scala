// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings

object PartitioningsProto extends _root_.scalapb.GeneratedFileObject {
  lazy val dependencies: Seq[_root_.scalapb.GeneratedFileObject] = Seq(
    scalapb.options.ScalapbProto,
    edu.uci.ics.amber.engine.common.virtualidentity.VirtualidentityProto
  )
  lazy val messagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] =
    Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]](
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.PartitioningMessage,
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.OneToOnePartitioning,
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.RoundRobinPartitioning,
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.HashBasedShufflePartitioning,
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.RangeBasedShufflePartitioning,
      edu.uci.ics.amber.engine.architecture.sendsemantics.partitionings.BroadcastPartitioning
    )
  private lazy val ProtoBytes: _root_.scala.Array[Byte] =
      scalapb.Encoding.fromBase64(scala.collection.immutable.Seq(
  """CkdlZHUvdWNpL2ljcy9hbWJlci9lbmdpbmUvYXJjaGl0ZWN0dXJlL3NlbmRzZW1hbnRpY3MvcGFydGl0aW9uaW5ncy5wcm90b
  xIzZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS5zZW5kc2VtYW50aWNzGhVzY2FsYXBiL3NjYWxhcGIucHJvd
  G8aNWVkdS91Y2kvaWNzL2FtYmVyL2VuZ2luZS9jb21tb24vdmlydHVhbGlkZW50aXR5LnByb3RvIv8GCgxQYXJ0aXRpb25pbmcSm
  gEKFG9uZVRvT25lUGFydGl0aW9uaW5nGAEgASgLMkkuZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS5zZW5kc
  2VtYW50aWNzLk9uZVRvT25lUGFydGl0aW9uaW5nQhniPxYSFG9uZVRvT25lUGFydGl0aW9uaW5nSABSFG9uZVRvT25lUGFydGl0a
  W9uaW5nEqIBChZyb3VuZFJvYmluUGFydGl0aW9uaW5nGAIgASgLMksuZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmFyY2hpdGVjd
  HVyZS5zZW5kc2VtYW50aWNzLlJvdW5kUm9iaW5QYXJ0aXRpb25pbmdCG+I/GBIWcm91bmRSb2JpblBhcnRpdGlvbmluZ0gAUhZyb
  3VuZFJvYmluUGFydGl0aW9uaW5nEroBChxoYXNoQmFzZWRTaHVmZmxlUGFydGl0aW9uaW5nGAMgASgLMlEuZWR1LnVjaS5pY3MuY
  W1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS5zZW5kc2VtYW50aWNzLkhhc2hCYXNlZFNodWZmbGVQYXJ0aXRpb25pbmdCIeI/HhIca
  GFzaEJhc2VkU2h1ZmZsZVBhcnRpdGlvbmluZ0gAUhxoYXNoQmFzZWRTaHVmZmxlUGFydGl0aW9uaW5nEr4BCh1yYW5nZUJhc2VkU
  2h1ZmZsZVBhcnRpdGlvbmluZxgEIAEoCzJSLmVkdS51Y2kuaWNzLmFtYmVyLmVuZ2luZS5hcmNoaXRlY3R1cmUuc2VuZHNlbWFud
  Gljcy5SYW5nZUJhc2VkU2h1ZmZsZVBhcnRpdGlvbmluZ0Ii4j8fEh1yYW5nZUJhc2VkU2h1ZmZsZVBhcnRpdGlvbmluZ0gAUh1yY
  W5nZUJhc2VkU2h1ZmZsZVBhcnRpdGlvbmluZxKeAQoVYnJvYWRjYXN0UGFydGl0aW9uaW5nGAUgASgLMkouZWR1LnVjaS5pY3MuY
  W1iZXIuZW5naW5lLmFyY2hpdGVjdHVyZS5zZW5kc2VtYW50aWNzLkJyb2FkY2FzdFBhcnRpdGlvbmluZ0Ia4j8XEhVicm9hZGNhc
  3RQYXJ0aXRpb25pbmdIAFIVYnJvYWRjYXN0UGFydGl0aW9uaW5nQg4KDHNlYWxlZF92YWx1ZSKpAQoUT25lVG9PbmVQYXJ0aXRpb
  25pbmcSLAoJYmF0Y2hTaXplGAEgASgFQg7iPwsSCWJhdGNoU2l6ZVIJYmF0Y2hTaXplEmMKCXJlY2VpdmVycxgCIAMoCzI1LmVkd
  S51Y2kuaWNzLmFtYmVyLmVuZ2luZS5jb21tb24uQWN0b3JWaXJ0dWFsSWRlbnRpdHlCDuI/CxIJcmVjZWl2ZXJzUglyZWNlaXZlc
  nMiqwEKFlJvdW5kUm9iaW5QYXJ0aXRpb25pbmcSLAoJYmF0Y2hTaXplGAEgASgFQg7iPwsSCWJhdGNoU2l6ZVIJYmF0Y2hTaXplE
  mMKCXJlY2VpdmVycxgCIAMoCzI1LmVkdS51Y2kuaWNzLmFtYmVyLmVuZ2luZS5jb21tb24uQWN0b3JWaXJ0dWFsSWRlbnRpdHlCD
  uI/CxIJcmVjZWl2ZXJzUglyZWNlaXZlcnMi+gEKHEhhc2hCYXNlZFNodWZmbGVQYXJ0aXRpb25pbmcSLAoJYmF0Y2hTaXplGAEgA
  SgFQg7iPwsSCWJhdGNoU2l6ZVIJYmF0Y2hTaXplEmMKCXJlY2VpdmVycxgCIAMoCzI1LmVkdS51Y2kuaWNzLmFtYmVyLmVuZ2luZ
  S5jb21tb24uQWN0b3JWaXJ0dWFsSWRlbnRpdHlCDuI/CxIJcmVjZWl2ZXJzUglyZWNlaXZlcnMSRwoSaGFzaEF0dHJpYnV0ZU5hb
  WVzGAMgAygJQhfiPxQSEmhhc2hBdHRyaWJ1dGVOYW1lc1ISaGFzaEF0dHJpYnV0ZU5hbWVzItQCCh1SYW5nZUJhc2VkU2h1ZmZsZ
  VBhcnRpdGlvbmluZxIsCgliYXRjaFNpemUYASABKAVCDuI/CxIJYmF0Y2hTaXplUgliYXRjaFNpemUSYwoJcmVjZWl2ZXJzGAIgA
  ygLMjUuZWR1LnVjaS5pY3MuYW1iZXIuZW5naW5lLmNvbW1vbi5BY3RvclZpcnR1YWxJZGVudGl0eUIO4j8LEglyZWNlaXZlcnNSC
  XJlY2VpdmVycxJKChNyYW5nZUF0dHJpYnV0ZU5hbWVzGAMgAygJQhjiPxUSE3JhbmdlQXR0cmlidXRlTmFtZXNSE3JhbmdlQXR0c
  mlidXRlTmFtZXMSKQoIcmFuZ2VNaW4YBCABKANCDeI/ChIIcmFuZ2VNaW5SCHJhbmdlTWluEikKCHJhbmdlTWF4GAUgASgDQg3iP
  woSCHJhbmdlTWF4UghyYW5nZU1heCKqAQoVQnJvYWRjYXN0UGFydGl0aW9uaW5nEiwKCWJhdGNoU2l6ZRgBIAEoBUIO4j8LEgliY
  XRjaFNpemVSCWJhdGNoU2l6ZRJjCglyZWNlaXZlcnMYAiADKAsyNS5lZHUudWNpLmljcy5hbWJlci5lbmdpbmUuY29tbW9uLkFjd
  G9yVmlydHVhbElkZW50aXR5Qg7iPwsSCXJlY2VpdmVyc1IJcmVjZWl2ZXJzQgniPwZIAFgAeAFiBnByb3RvMw=="""
      ).mkString)
  lazy val scalaDescriptor: _root_.scalapb.descriptors.FileDescriptor = {
    val scalaProto = com.google.protobuf.descriptor.FileDescriptorProto.parseFrom(ProtoBytes)
    _root_.scalapb.descriptors.FileDescriptor.buildFrom(scalaProto, dependencies.map(_.scalaDescriptor))
  }
  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val javaProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto.parseFrom(ProtoBytes)
    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(javaProto, _root_.scala.Array(
      scalapb.options.ScalapbProto.javaDescriptor,
      edu.uci.ics.amber.engine.common.virtualidentity.VirtualidentityProto.javaDescriptor
    ))
  }
  @deprecated("Use javaDescriptor instead. In a future version this will refer to scalaDescriptor.", "ScalaPB 0.5.47")
  def descriptor: com.google.protobuf.Descriptors.FileDescriptor = javaDescriptor
}