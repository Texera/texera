package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.ControllerAsyncRPCHandlerInitializer
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.EvaluatePythonExpressionHandler.EvaluatePythonExpression
import edu.uci.ics.amber.engine.architecture.pythonworker.promisehandlers.EvaluateExpressionHandler.EvaluateExpression
import edu.uci.ics.amber.engine.architecture.worker.controlreturns.EvaluatedValue
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.ControlCommand
import edu.uci.ics.texera.web.model.websocket.response.python.PythonExpressionEvaluateResponse

object EvaluatePythonExpressionHandler {
  final case class EvaluatePythonExpression(expression: String, operatorId: String)
      extends ControlCommand[List[EvaluatedValue]]
}

trait EvaluatePythonExpressionHandler {
  this: ControllerAsyncRPCHandlerInitializer =>
  registerHandler { (msg: EvaluatePythonExpression, sender) =>
    {

      Future
        .collect(
          workflow
            .getOperator(msg.operatorId)
            .getAllWorkers
            .map(worker => send(EvaluateExpression(msg.expression), worker))
            .toList
        )
        .map(evaluatedValues => {
          evaluatedValues.toList
        })
    }
  }
}
