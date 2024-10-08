package edu.uci.ics.texera.web.resource.languageserver

import edu.uci.ics.amber.engine.common.AmberConfig

import java.util.logging.Logger
import scala.sys.process._

import java.io.File

object PythonLanguageServerManager {
  private val pythonLanguageServerConfig = AmberConfig.pythonLanguageServerConfig
  val pythonLanguageServerProvider: String = pythonLanguageServerConfig.getString("provider")
  val pythonLanguageServerPort: Int = pythonLanguageServerConfig.getInt("port")
  private val logger = Logger.getLogger("PythonLanguageServerManager")

  // For retry
  private val MAX_TRY_COUNT: Int = pythonLanguageServerConfig.getInt("retry-counts")
  private val UNIT_WAIT_TIME_MS = pythonLanguageServerConfig.getInt("wait-time-ms")

  // To start the python language server based on the python-language-server provider
  def startLanguageServer(): Unit = {
    pythonLanguageServerProvider match {
      // The situation when the provider is Pyright
      case "pyright" =>
        logger.info("Starting Pyright...")
        var tryCount = 0
        var started = false
        while (tryCount < MAX_TRY_COUNT && !started) {
          try {
            val command = Seq("node", "--loader", "ts-node/esm", "src/main.ts")
            val workingDir = new File("../pyright-language-server")
            val exitCode = Process(command, workingDir).!
            if (exitCode == 0) {
              logger.info(
                s"Pyright language server started successfully on port $pythonLanguageServerPort"
              )
              started = true
            } else {
              logger.warning(
                s"Pyright failed to start with exit code: $exitCode (attempt ${tryCount + 1}/$MAX_TRY_COUNT)"
              )
            }
          } catch {
            case e: Exception =>
              logger.warning(
                s"Failed to start Pyright (attempt ${tryCount + 1}/$MAX_TRY_COUNT): ${e.getMessage}"
              )
          }
          if (!started && tryCount < MAX_TRY_COUNT - 1) {
            logger.info(s"Retrying in $UNIT_WAIT_TIME_MS ms...")
            Thread.sleep(UNIT_WAIT_TIME_MS)
          }
          tryCount += 1
        }
        if (!started) {
          logger.warning(s"Failed to start Pyright after $MAX_TRY_COUNT attempts. Abort!")
        }

      // The situation when the provider is Pylsp
      case "pylsp" =>
        logger.info("Starting Pylsp...")
        var tryCount = 0
        var started = false
        while (tryCount < MAX_TRY_COUNT && !started) {
          try {
            val result = {
              Process(s"pylsp --ws --port $pythonLanguageServerPort").run(
                ProcessLogger(_ => (), err => logger.warning(s"Error during Pylsp startup: $err"))
              )
            }
            logger.info(s"Pylsp language server is running on port $pythonLanguageServerPort")
            started = true
          } catch {
            case e: Exception =>
              logger.warning(
                s"Failed to start Pylsp (attempt ${tryCount + 1}/$MAX_TRY_COUNT): ${e.getMessage}"
              )
              if (tryCount < MAX_TRY_COUNT - 1) {
                logger.info(s"Retrying in $UNIT_WAIT_TIME_MS ms...")
                Thread.sleep(UNIT_WAIT_TIME_MS)
              }
              tryCount += 1
          }
        }
        if (!started) {
          logger.warning(s"Failed to start Pylsp after $MAX_TRY_COUNT attempts. Abort!")
        }

      case _ =>
        logger.warning(s"Unknown language server: $pythonLanguageServerPort")
    }
  }
}
