package edu.uci.ics.texera.web

import javax.ws.rs.container.{
  ContainerRequestContext,
  ContainerResponseContext,
  ContainerResponseFilter
}
import javax.ws.rs.ext.Provider
import javax.ws.rs.core.MultivaluedMap

@Provider
class CORSFilter extends ContainerResponseFilter {
  override def filter(
      requestContext: ContainerRequestContext,
      responseContext: ContainerResponseContext
  ): Unit = {
    val headers: MultivaluedMap[String, AnyRef] = responseContext.getHeaders
    headers.add("Access-Control-Allow-Origin", "*")
    headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS")
    headers.add("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept")
    headers.add("Access-Control-Allow-Credentials", "true")
  }
}
