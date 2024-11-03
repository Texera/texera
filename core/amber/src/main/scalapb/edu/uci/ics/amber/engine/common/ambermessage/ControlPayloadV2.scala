// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package edu.uci.ics.amber.engine.common.ambermessage

@SerialVersionUID(0L)
final case class ControlPayloadV2(
    value: edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[ControlPayloadV2] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      if (value.controlInvocation.isDefined) {
        val __value = value.controlInvocation.get
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      };
      if (value.returnInvocation.isDefined) {
        val __value = value.returnInvocation.get
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      };
      __size
    }
    override def serializedSize: _root_.scala.Int = {
      var __size = __serializedSizeMemoized
      if (__size == 0) {
        __size = __computeSerializedSize() + 1
        __serializedSizeMemoized = __size
      }
      __size - 1
      
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      value.controlInvocation.foreach { __v =>
        val __m = __v
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      value.returnInvocation.foreach { __v =>
        val __m = __v
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def getControlInvocation: edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation = value.controlInvocation.getOrElse(edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation.defaultInstance)
    def withControlInvocation(__v: edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation): ControlPayloadV2 = copy(value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ControlInvocation(__v))
    def getReturnInvocation: edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation = value.returnInvocation.getOrElse(edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation.defaultInstance)
    def withReturnInvocation(__v: edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation): ControlPayloadV2 = copy(value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ReturnInvocation(__v))
    def clearValue: ControlPayloadV2 = copy(value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.Empty)
    def withValue(__v: edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value): ControlPayloadV2 = copy(value = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => value.controlInvocation.orNull
        case 2 => value.returnInvocation.orNull
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => value.controlInvocation.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 2 => value.returnInvocation.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToSingleLineUnicodeString(this)
    def companion: edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.type = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2
    // @@protoc_insertion_point(GeneratedMessage[edu.uci.ics.amber.engine.common.ControlPayloadV2])
}

object ControlPayloadV2 extends scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2 = {
    var __value: edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.Empty
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ControlInvocation(__value.controlInvocation.fold(_root_.scalapb.LiteParser.readMessage[edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case 18 =>
          __value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ReturnInvocation(__value.returnInvocation.fold(_root_.scalapb.LiteParser.readMessage[edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case tag => _input__.skipField(tag)
      }
    }
    edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2(
        value = __value
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2(
        value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).flatMap(_.as[_root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation]]).map(edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ControlInvocation(_))
            .orElse[edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value](__fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation]]).map(edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ReturnInvocation(_)))
            .getOrElse(edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.Empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = AmbermessageProto.javaDescriptor.getMessageTypes().get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = AmbermessageProto.scalaDescriptor.messages(0)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation
      case 2 => __out = edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2(
    value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.Empty
  )
  sealed trait Value extends _root_.scalapb.GeneratedOneof {
    def isEmpty: _root_.scala.Boolean = false
    def isDefined: _root_.scala.Boolean = true
    def isControlInvocation: _root_.scala.Boolean = false
    def isReturnInvocation: _root_.scala.Boolean = false
    def controlInvocation: _root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation] = _root_.scala.None
    def returnInvocation: _root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation] = _root_.scala.None
  }
  object Value {
    @SerialVersionUID(0L)
    case object Empty extends edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value {
      type ValueType = _root_.scala.Nothing
      override def isEmpty: _root_.scala.Boolean = true
      override def isDefined: _root_.scala.Boolean = false
      override def number: _root_.scala.Int = 0
      override def value: _root_.scala.Nothing = throw new java.util.NoSuchElementException("Empty.value")
    }
  
    @SerialVersionUID(0L)
    final case class ControlInvocation(value: edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation) extends edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value {
      type ValueType = edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation
      override def isControlInvocation: _root_.scala.Boolean = true
      override def controlInvocation: _root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation] = Some(value)
      override def number: _root_.scala.Int = 1
    }
    @SerialVersionUID(0L)
    final case class ReturnInvocation(value: edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation) extends edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value {
      type ValueType = edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation
      override def isReturnInvocation: _root_.scala.Boolean = true
      override def returnInvocation: _root_.scala.Option[edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation] = Some(value)
      override def number: _root_.scala.Int = 2
    }
  }
  implicit class ControlPayloadV2Lens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2](_l) {
    def controlInvocation: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.architecture.rpc.controlcommands.ControlInvocation] = field(_.getControlInvocation)((c_, f_) => c_.copy(value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ControlInvocation(f_)))
    def returnInvocation: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.architecture.rpc.controlreturns.ReturnInvocation] = field(_.getReturnInvocation)((c_, f_) => c_.copy(value = edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value.ReturnInvocation(f_)))
    def value: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value] = field(_.value)((c_, f_) => c_.copy(value = f_))
  }
  final val CONTROL_INVOCATION_FIELD_NUMBER = 1
  final val RETURN_INVOCATION_FIELD_NUMBER = 2
  def of(
    value: edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2.Value
  ): _root_.edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2 = _root_.edu.uci.ics.amber.engine.common.ambermessage.ControlPayloadV2(
    value
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.ControlPayloadV2])
}