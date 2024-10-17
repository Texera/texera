package edu.uci.ics.texera.compilation

import edu.uci.ics.texera.compilation.PathUtils.webConfigFilePath
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
    val configFilePath = webConfigFilePath.toAbsolutePath.toString

    // Start the Dropwizard application with the specified configuration file path
    new WorkflowCompilingServiceWebApplication().run("server", configFilePath)
  }
}
