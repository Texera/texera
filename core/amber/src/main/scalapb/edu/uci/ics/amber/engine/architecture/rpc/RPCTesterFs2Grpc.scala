package edu.uci.ics.amber.engine.architecture.rpc

import _root_.cats.syntax.all._

trait RPCTesterFs2Grpc[F[_], A] {
  def sendPing(request: edu.uci.ics.amber.engine.architecture.rpc.Ping, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse]
  def sendPong(request: edu.uci.ics.amber.engine.architecture.rpc.Pong, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse]
  def sendNested(request: edu.uci.ics.amber.engine.architecture.rpc.Nested, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendPass(request: edu.uci.ics.amber.engine.architecture.rpc.Pass, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendErrorCommand(request: edu.uci.ics.amber.engine.architecture.rpc.ErrorCommand, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendRecursion(request: edu.uci.ics.amber.engine.architecture.rpc.Recursion, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendCollect(request: edu.uci.ics.amber.engine.architecture.rpc.Collect, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendGenerateNumber(request: edu.uci.ics.amber.engine.architecture.rpc.GenerateNumber, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse]
  def sendMultiCall(request: edu.uci.ics.amber.engine.architecture.rpc.MultiCall, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
  def sendChain(request: edu.uci.ics.amber.engine.architecture.rpc.Chain, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse]
}

object RPCTesterFs2Grpc extends _root_.fs2.grpc.GeneratedCompanion[RPCTesterFs2Grpc] {
  
  def mkClient[F[_]: _root_.cats.effect.Async, A](dispatcher: _root_.cats.effect.std.Dispatcher[F], channel: _root_.io.grpc.Channel, mkMetadata: A => F[_root_.io.grpc.Metadata], clientOptions: _root_.fs2.grpc.client.ClientOptions): RPCTesterFs2Grpc[F, A] = new RPCTesterFs2Grpc[F, A] {
    def sendPing(request: edu.uci.ics.amber.engine.architecture.rpc.Ping, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PING, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendPong(request: edu.uci.ics.amber.engine.architecture.rpc.Pong, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PONG, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendNested(request: edu.uci.ics.amber.engine.architecture.rpc.Nested, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_NESTED, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendPass(request: edu.uci.ics.amber.engine.architecture.rpc.Pass, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PASS, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendErrorCommand(request: edu.uci.ics.amber.engine.architecture.rpc.ErrorCommand, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_ERROR_COMMAND, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendRecursion(request: edu.uci.ics.amber.engine.architecture.rpc.Recursion, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_RECURSION, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendCollect(request: edu.uci.ics.amber.engine.architecture.rpc.Collect, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_COLLECT, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendGenerateNumber(request: edu.uci.ics.amber.engine.architecture.rpc.GenerateNumber, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.IntResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_GENERATE_NUMBER, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendMultiCall(request: edu.uci.ics.amber.engine.architecture.rpc.MultiCall, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_MULTI_CALL, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
    def sendChain(request: edu.uci.ics.amber.engine.architecture.rpc.Chain, ctx: A): F[edu.uci.ics.amber.engine.architecture.rpc.StringResponse] = {
      mkMetadata(ctx).flatMap { m =>
        _root_.fs2.grpc.client.Fs2ClientCall[F](channel, edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_CHAIN, dispatcher, clientOptions).flatMap(_.unaryToUnaryCall(request, m))
      }
    }
  }
  
  protected def serviceBinding[F[_]: _root_.cats.effect.Async, A](dispatcher: _root_.cats.effect.std.Dispatcher[F], serviceImpl: RPCTesterFs2Grpc[F, A], mkCtx: _root_.io.grpc.Metadata => F[A], serverOptions: _root_.fs2.grpc.server.ServerOptions): _root_.io.grpc.ServerServiceDefinition = {
    _root_.io.grpc.ServerServiceDefinition
      .builder(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.SERVICE)
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PING, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Ping, edu.uci.ics.amber.engine.architecture.rpc.IntResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendPing(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PONG, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Pong, edu.uci.ics.amber.engine.architecture.rpc.IntResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendPong(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_NESTED, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Nested, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendNested(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_PASS, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Pass, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendPass(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_ERROR_COMMAND, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.ErrorCommand, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendErrorCommand(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_RECURSION, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Recursion, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendRecursion(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_COLLECT, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Collect, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendCollect(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_GENERATE_NUMBER, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.GenerateNumber, edu.uci.ics.amber.engine.architecture.rpc.IntResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendGenerateNumber(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_MULTI_CALL, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.MultiCall, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendMultiCall(r, _))))
      .addMethod(edu.uci.ics.amber.engine.architecture.rpc.RPCTesterGrpc.METHOD_SEND_CHAIN, _root_.fs2.grpc.server.Fs2ServerCallHandler[F](dispatcher, serverOptions).unaryToUnaryCall[edu.uci.ics.amber.engine.architecture.rpc.Chain, edu.uci.ics.amber.engine.architecture.rpc.StringResponse]((r, m) => mkCtx(m).flatMap(serviceImpl.sendChain(r, _))))
      .build()
  }

}