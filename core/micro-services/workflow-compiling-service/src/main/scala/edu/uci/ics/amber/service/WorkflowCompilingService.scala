package edu.uci.ics.amber.service

import io.dropwizard.core.Application
import io.dropwizard.core.setup.{Bootstrap, Environment}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import edu.uci.ics.amber.compiler.util.PathUtil.webConfigFilePath
import edu.uci.ics.amber.service.resource.WorkflowCompilationResource

class WorkflowCompilingService extends Application[WorkflowCompilingServiceConfiguration] {
  override def initialize(bootstrap: Bootstrap[WorkflowCompilingServiceConfiguration]): Unit = {
    // register scala module to dropwizard default object mapper
    bootstrap.getObjectMapper.registerModule(DefaultScalaModule)
  }

  override def run(
      configuration: WorkflowCompilingServiceConfiguration,
      environment: Environment
  ): Unit = {
    // serve backend at /api/texera
    environment.jersey.setUrlPattern("/api/texera/*")
    // register CORS filter
    environment.jersey.register(classOf[CORSFilter])
    // register the compilation endpoint
    environment.jersey.register(classOf[WorkflowCompilationResource])
  }
}

object WorkflowCompilingService {
  def main(args: Array[String]): Unit = {
    // set the configuration file's path
    val configFilePath = webConfigFilePath.toAbsolutePath.toString

    // Start the Dropwizard application
    new WorkflowCompilingService().run("server", configFilePath)
  }
}