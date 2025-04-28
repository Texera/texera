package edu.uci.ics.texera.web.resource

import edu.uci.ics.texera.dao.jooq.generated.enums.UserRoleEnum

/**
  * EmailTemplate provides factory methods to generate email messages
  * for different user notification scenarios.
  */
object EmailTemplate {

  /**
    * Creates an email message for user registration notifications.
    * Depending on the 'toAdmin' flag, it either notifies an administrator
    * of a pending account request or acknowledges receipt to the user.
    *
    * @param receiverEmail the email address of the receiver (admin or user)
    * @param userEmail optional; the email address of the user requesting an account (only needed if toAdmin is true)
    * @param toAdmin flag indicating whether the notification is for the admin (true) or the user (false)
    * @return an EmailMessage ready to be sent
    */
  def userRegistrationNotification(
      receiverEmail: String,
      userEmail: Option[String],
      toAdmin: Boolean
  ): EmailMessage = {
    if (toAdmin) {
      val subject = "New Account Request Pending Approval"
      val content =
        s"""
           |Hello Admin,
           |
           |A new user has attempted to log in or register, but their account is not yet approved.
           |Please review the account request for the following email:
           |
           |${userEmail.getOrElse("Unknown")}
           |
           |Thanks!
           |""".stripMargin
      EmailMessage(subject = subject, content = content, receiver = receiverEmail)
    } else {
      val subject = "Account Request Received"
      val content =
        s"""
           |Hello,
           |
           |Thank you for submitting your account request.
           |We have received your request and it is currently under review.
           |Please be patient during this process. You will be notified once your account has been approved.
           |
           |Thank you for your interest!
           |""".stripMargin
      EmailMessage(subject = subject, content = content, receiver = receiverEmail)
    }
  }

  /**
    * Creates an email message to notify a user
    * that their role has been updated.
    *
    * @param receiverEmail the user's email address
    * @param newRole the new role assigned to the user
    * @return an EmailMessage ready to be sent to the user
    */
  def createRoleChangeTemplate(receiverEmail: String, newRole: UserRoleEnum): EmailMessage = {
    val subject = "Your Role Has Been Updated"
    val content =
      s"""
         |Hello,
         |
         |Your user role has been updated to: $newRole.
         |
         |If you have any questions, please contact the administrator.
         |
         |Thank you!
         |""".stripMargin

    EmailMessage(subject = subject, content = content, receiver = receiverEmail)
  }
}
