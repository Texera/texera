package edu.uci.ics.amber.engine.architecture.rpc

import _root_.cats.syntax.all._

trait WorkerServiceFs2Grpc[F[_], A] {
  def addInputChannel(request: edu.uci.ics.amber.engine.architecture.rpc.AddInputChannelRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def addPartitioning(request: edu.uci.ics.amber.engine.architecture.rpc.AddPartitioningRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def assignPort(request: edu.uci.ics.amber.engine.architecture.rpc.AssignPortRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def finalizeCheckpoint(request: edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointResponse]
  def flushNetworkBuffer(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def initializeExecutor(request: edu.uci.ics.amber.engine.architecture.rpc.InitializeExecutorRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def openExecutor(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def pauseWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]
  def prepareCheckpoint(request: edu.uci.ics.amber.engine.architecture.rpc.PrepareCheckpointRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def queryStatistics(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerMetricsResponse]
  def resumeWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]
  def retrieveState(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def retryCurrentTuple(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def startWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]
  def debugCommand(request: edu.uci.ics.amber.engine.architecture.rpc.DebugCommandRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
  def evaluatePythonExpression(request: edu.uci.ics.amber.engine.architecture.rpc.EvaluatePythonExpressionRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EvaluatedValue]
  def noOperation(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]
}

object WorkerServiceFs2Grpc extends _root_.fs2.grpc.GeneratedCompanion[WorkerServiceFs2Grpc] {
  
  def mkClient[F[_]: _root_.cats.effect.Async, A](dispatcher: _root_.cats.effect.std.Dispatcher[F], channel: _root_.io.grpc.Channel, mkMetadata: A => F[_root_.io.grpc.Metadata], clientOptions: _root_.fs2.grpc.client.ClientOptions): WorkerServiceFs2Grpc[F, A] = new WorkerServiceFs2Grpc[F, A] {
    def addInputChannel(request: edu.uci.ics.amber.engine.architecture.rpc.AddInputChannelRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ADD_INPUT_CHANNEL, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def addPartitioning(request: edu.uci.ics.amber.engine.architecture.rpc.AddPartitioningRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ADD_PARTITIONING, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def assignPort(request: edu.uci.ics.amber.engine.architecture.rpc.AssignPortRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ASSIGN_PORT, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def finalizeCheckpoint(request: edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_FINALIZE_CHECKPOINT, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def flushNetworkBuffer(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_FLUSH_NETWORK_BUFFER, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def initializeExecutor(request: edu.uci.ics.amber.engine.architecture.rpc.InitializeExecutorRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_INITIALIZE_EXECUTOR, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def openExecutor(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_OPEN_EXECUTOR, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def pauseWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_PAUSE_WORKER, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def prepareCheckpoint(request: edu.uci.ics.amber.engine.architecture.rpc.PrepareCheckpointRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_PREPARE_CHECKPOINT, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def queryStatistics(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerMetricsResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_QUERY_STATISTICS, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def resumeWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RESUME_WORKER, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def retrieveState(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RETRIEVE_STATE, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def retryCurrentTuple(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RETRY_CURRENT_TUPLE, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def startWorker(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_START_WORKER, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def debugCommand(request: edu.uci.ics.amber.engine.architecture.rpc.DebugCommandRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_DEBUG_COMMAND, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def evaluatePythonExpression(request: edu.uci.ics.amber.engine.architecture.rpc.EvaluatePythonExpressionRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EvaluatedValue] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_EVALUATE_PYTHON_EXPRESSION, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def noOperation(request: edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_NO_OPERATION, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
  }
  
  protected def serviceBinding[F[_]: _root_.cats.effect.Async, A](dispatcher: _root_.cats.effect.std.Dispatcher[F], serviceImpl: WorkerServiceFs2Grpc[F, A], mkCtx: _root_.io.grpc.Metadata => F[A], serverOptions: _root_.fs2.grpc.server.ServerOptions): _root_.io.grpc.ServerServiceDefinition = {
    _root_.io.grpc.ServerServiceDefinition
      .builder(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.SERVICE)
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ADD_INPUT_CHANNEL, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.AddInputChannelRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.addInputChannel(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ADD_PARTITIONING, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.AddPartitioningRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.addPartitioning(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_ASSIGN_PORT, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.AssignPortRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.assignPort(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_FINALIZE_CHECKPOINT, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointRequest, edu.uci.ics.amber.engine.architecture.rpc.FinalizeCheckpointResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.finalizeCheckpoint(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_FLUSH_NETWORK_BUFFER, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.flushNetworkBuffer(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_INITIALIZE_EXECUTOR, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.InitializeExecutorRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.initializeExecutor(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_OPEN_EXECUTOR, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.openExecutor(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_PAUSE_WORKER, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.pauseWorker(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_PREPARE_CHECKPOINT, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.PrepareCheckpointRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.prepareCheckpoint(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_QUERY_STATISTICS, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.WorkerMetricsResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.queryStatistics(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RESUME_WORKER, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.resumeWorker(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RETRIEVE_STATE, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.retrieveState(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_RETRY_CURRENT_TUPLE, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.retryCurrentTuple(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_START_WORKER, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.WorkerStateResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.startWorker(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_DEBUG_COMMAND, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.DebugCommandRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.debugCommand(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_EVALUATE_PYTHON_EXPRESSION, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EvaluatePythonExpressionRequest, edu.uci.ics.amber.engine.architecture.rpc.EvaluatedValue]((r, m) => mkCtx(m).flatMap(serviceImpl.evaluatePythonExpression(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.WorkerServiceGrpc.METHOD_NO_OPERATION, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.EmptyRequest, edu.uci.ics.amber.engine.architecture.rpc.EmptyReturn]((r, m) => mkCtx(m).flatMap(serviceImpl.noOperation(r, _))))
      .build()
  }

}