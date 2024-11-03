// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package edu.uci.ics.amber.engine.common.workflowruntimestate

@SerialVersionUID(0L)
final case class ExecutionConsoleStore(
    operatorConsole: _root_.scala.collection.immutable.Map[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole] = _root_.scala.collection.immutable.Map.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[ExecutionConsoleStore] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      operatorConsole.foreach { __item =>
        val __value = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toBase(__item)
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
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
      operatorConsole.foreach { __v =>
        val __m = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toBase(__v)
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def clearOperatorConsole = copy(operatorConsole = _root_.scala.collection.immutable.Map.empty)
    def addOperatorConsole(__vs: (_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole) *): ExecutionConsoleStore = addAllOperatorConsole(__vs)
    def addAllOperatorConsole(__vs: Iterable[(_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole)]): ExecutionConsoleStore = copy(operatorConsole = operatorConsole ++ __vs)
    def withOperatorConsole(__v: _root_.scala.collection.immutable.Map[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]): ExecutionConsoleStore = copy(operatorConsole = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => operatorConsole.iterator.map(edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toBase(_)).toSeq
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PRepeated(operatorConsole.iterator.map(edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toBase(_).toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToSingleLineUnicodeString(this)
    def companion: edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.type = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore
    // @@protoc_insertion_point(GeneratedMessage[edu.uci.ics.amber.engine.common.ExecutionConsoleStore])
}

object ExecutionConsoleStore extends scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore = {
    val __operatorConsole: _root_.scala.collection.mutable.Builder[(_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole), _root_.scala.collection.immutable.Map[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]] = _root_.scala.collection.immutable.Map.newBuilder[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __operatorConsole += edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toCustom(_root_.scalapb.LiteParser.readMessage[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry](_input__))
        case tag => _input__.skipField(tag)
      }
    }
    edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore(
        operatorConsole = __operatorConsole.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore(
        operatorConsole = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Seq[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry]]).getOrElse(_root_.scala.Seq.empty).iterator.map(edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore._typemapper_operatorConsole.toCustom(_)).toMap
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = WorkflowruntimestateProto.javaDescriptor.getMessageTypes().get(5)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = WorkflowruntimestateProto.scalaDescriptor.messages(5)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] =
    Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]](
      _root_.edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry
    )
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore(
    operatorConsole = _root_.scala.collection.immutable.Map.empty
  )
  @SerialVersionUID(0L)
  final case class OperatorConsoleEntry(
      key: _root_.scala.Predef.String = "",
      value: _root_.scala.Option[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole] = _root_.scala.None
      ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[OperatorConsoleEntry] {
      @transient
      private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
      private[this] def __computeSerializedSize(): _root_.scala.Int = {
        var __size = 0
        
        {
          val __value = key
          if (!__value.isEmpty) {
            __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
          }
        };
        if (value.isDefined) {
          val __value = value.get
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
        {
          val __v = key
          if (!__v.isEmpty) {
            _output__.writeString(1, __v)
          }
        };
        value.foreach { __v =>
          val __m = __v
          _output__.writeTag(2, 2)
          _output__.writeUInt32NoTag(__m.serializedSize)
          __m.writeTo(_output__)
        };
      }
      def withKey(__v: _root_.scala.Predef.String): OperatorConsoleEntry = copy(key = __v)
      def getValue: edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole = value.getOrElse(edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole.defaultInstance)
      def clearValue: OperatorConsoleEntry = copy(value = _root_.scala.None)
      def withValue(__v: edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole): OperatorConsoleEntry = copy(value = Option(__v))
      def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
        (__fieldNumber: @_root_.scala.unchecked) match {
          case 1 => {
            val __t = key
            if (__t != "") __t else null
          }
          case 2 => value.orNull
        }
      }
      def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
        _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
        (__field.number: @_root_.scala.unchecked) match {
          case 1 => _root_.scalapb.descriptors.PString(key)
          case 2 => value.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        }
      }
      def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToSingleLineUnicodeString(this)
      def companion: edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry.type = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry
      // @@protoc_insertion_point(GeneratedMessage[edu.uci.ics.amber.engine.common.ExecutionConsoleStore.OperatorConsoleEntry])
  }
  
  object OperatorConsoleEntry extends scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry] {
    implicit def messageCompanion: scalapb.GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry] = this
    def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry = {
      var __key: _root_.scala.Predef.String = ""
      var __value: _root_.scala.Option[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole] = _root_.scala.None
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __key = _input__.readStringRequireUtf8()
          case 18 =>
            __value = Option(__value.fold(_root_.scalapb.LiteParser.readMessage[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
          case tag => _input__.skipField(tag)
        }
      }
      edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry(
          key = __key,
          value = __value
      )
    }
    implicit def messageReads: _root_.scalapb.descriptors.Reads[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry] = _root_.scalapb.descriptors.Reads{
      case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
        _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
        edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry(
          key = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
          value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]])
        )
      case _ => throw new RuntimeException("Expected PMessage")
    }
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.javaDescriptor.getNestedTypes().get(0)
    def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.scalaDescriptor.nestedMessages(0)
    def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
      var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
      (__number: @_root_.scala.unchecked) match {
        case 2 => __out = edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole
      }
      __out
    }
    lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
    def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
    lazy val defaultInstance = edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry(
      key = "",
      value = _root_.scala.None
    )
    implicit class OperatorConsoleEntryLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry](_l) {
      def key: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.key)((c_, f_) => c_.copy(key = f_))
      def value: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole] = field(_.getValue)((c_, f_) => c_.copy(value = Option(f_)))
      def optionalValue: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]] = field(_.value)((c_, f_) => c_.copy(value = f_))
    }
    final val KEY_FIELD_NUMBER = 1
    final val VALUE_FIELD_NUMBER = 2
    @transient
    implicit val keyValueMapper: _root_.scalapb.TypeMapper[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry, (_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole)] =
      _root_.scalapb.TypeMapper[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry, (_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole)](__m => (__m.key, __m.getValue))(__p => edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry(__p._1, Some(__p._2)))
    def of(
      key: _root_.scala.Predef.String,
      value: _root_.scala.Option[edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]
    ): _root_.edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry = _root_.edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry(
      key,
      value
    )
    // @@protoc_insertion_point(GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.ExecutionConsoleStore.OperatorConsoleEntry])
  }
  
  implicit class ExecutionConsoleStoreLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore](_l) {
    def operatorConsole: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.collection.immutable.Map[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]] = field(_.operatorConsole)((c_, f_) => c_.copy(operatorConsole = f_))
  }
  final val OPERATOR_CONSOLE_FIELD_NUMBER = 1
  @transient
  private[workflowruntimestate] val _typemapper_operatorConsole: _root_.scalapb.TypeMapper[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry, (_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole)] = implicitly[_root_.scalapb.TypeMapper[edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore.OperatorConsoleEntry, (_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole)]]
  def of(
    operatorConsole: _root_.scala.collection.immutable.Map[_root_.scala.Predef.String, edu.uci.ics.amber.engine.common.workflowruntimestate.OperatorConsole]
  ): _root_.edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore = _root_.edu.uci.ics.amber.engine.common.workflowruntimestate.ExecutionConsoleStore(
    operatorConsole
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[edu.uci.ics.amber.engine.common.ExecutionConsoleStore])
}