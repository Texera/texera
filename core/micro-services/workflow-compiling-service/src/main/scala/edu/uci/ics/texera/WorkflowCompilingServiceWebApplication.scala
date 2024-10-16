package edu.uci.ics.texera

import io.dropwizard.core.Application
import io.dropwizard.core.setup.{Bootstrap, Environment}

class WorkflowCompilingServiceWebApplication extends Application[WorkflowCompilingServiceConfiguration]{
  override def initialize(bootstrap: Bootstrap[WorkflowCompilingServiceConfiguration]): Unit = {}

  override def run(configuration: WorkflowCompilingServiceConfiguration, environment: Environment): Unit = {
  }
}

object WorkflowCompilingServiceWebApplication {
  def main(args: Array[String]): Unit = {
    // Check if a configuration file path is passed; use a default if none is provided
    val configFilePath = if (args.nonEmpty) args(0) else "workflow-compiling-service/src/main/resources/web-config.yaml"

    // Start the Dropwizard application with the specified configuration file path
    new WorkflowCompilingServiceWebApplication().run("server", configFilePath)
  }
}
