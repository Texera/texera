package edu.uci.ics.texera.web.resource.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import edu.uci.ics.amber.engine.common.AmberConfig
import edu.uci.ics.texera.dao.SqlServer
import edu.uci.ics.texera.auth.JwtAuth.{TOKEN_EXPIRE_TIME_IN_DAYS, dayToMin, jwtClaims, jwtToken}
import edu.uci.ics.texera.web.model.http.response.TokenIssueResponse
import edu.uci.ics.texera.dao.jooq.generated.enums.UserRoleEnum
import edu.uci.ics.texera.dao.jooq.generated.tables.daos.UserDao
import edu.uci.ics.texera.dao.jooq.generated.tables.pojos.User
import edu.uci.ics.texera.web.resource.auth.GoogleAuthResource.userDao

import java.util.Collections
import javax.ws.rs._
import javax.ws.rs.core.MediaType

object GoogleAuthResource {
  final private lazy val userDao = new UserDao(
    SqlServer
      .getInstance()
      .createDSLContext()
      .configuration
  )
}

@Path("/auth/google")
class GoogleAuthResource {
  final private lazy val clientId = AmberConfig.googleClientId

  @GET
  @Path("/clientid")
  def getClientId: String = clientId

  @POST
  @Consumes(Array(MediaType.TEXT_PLAIN))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Path("/login")
  def login(credential: String): TokenIssueResponse = {
    if (!AmberConfig.isUserSystemEnabled)
      throw new NotAcceptableException("User System is disabled on the backend!")
    val idToken =
      new GoogleIdTokenVerifier.Builder(new NetHttpTransport, GsonFactory.getDefaultInstance)
        .setAudience(
          Collections.singletonList(clientId)
        )
        .build()
        .verify(credential)
    if (idToken != null) {
      val payload = idToken.getPayload
      val googleId = payload.getSubject
      val googleName = payload.get("name").asInstanceOf[String]
      val googleEmail = payload.getEmail
      val googleAvatar = payload.get("picture").asInstanceOf[String].split("/").last
      val user = Option(userDao.fetchOneByGoogleId(googleId)) match {
        case Some(user) =>
          if (user.getName != googleName) {
            user.setName(googleName)
            userDao.update(user)
          }
          if (user.getEmail != googleEmail) {
            user.setEmail(googleEmail)
            userDao.update(user)
          }
          if (user.getGoogleAvatar != googleAvatar) {
            user.setGoogleAvatar(googleAvatar)
            userDao.update(user)
          }
          user
        case None =>
          Option(userDao.fetchOneByEmail(googleEmail)) match {
            case Some(user) =>
              if (user.getName != googleName) {
                user.setName(googleName)
              }
              user.setGoogleId(googleId)
              user.setGoogleAvatar(googleAvatar)
              userDao.update(user)
              user
            case None =>
              // create a new user with googleId
              val user = new User
              user.setName(googleName)
              user.setEmail(googleEmail)
              user.setGoogleId(googleId)
              user.setRole(UserRoleEnum.INACTIVE)
              user.setGoogleAvatar(googleAvatar)
              userDao.insert(user)
              user
          }
      }
      TokenIssueResponse(jwtToken(jwtClaims(user, dayToMin(TOKEN_EXPIRE_TIME_IN_DAYS))))
    } else throw new NotAuthorizedException("Login credentials are incorrect.")
  }
}
