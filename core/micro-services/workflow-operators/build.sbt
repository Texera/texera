import scala.collection.Seq
/////////////////////////////////////////////////////////////////////////////
// Project Settings
/////////////////////////////////////////////////////////////////////////////

name := "workflow-operators"
organization := "edu.uci.ics"
version := "0.1.0"
scalaVersion := "2.13.12"

enablePlugins(JavaAppPackaging)

// Enable semanticdb for Scalafix
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Manage dependency conflicts by always using the latest revision
ThisBuild / conflictManager := ConflictManager.latestRevision

// Restrict parallel execution of tests to avoid conflicts
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)


/////////////////////////////////////////////////////////////////////////////
// Compiler Options
/////////////////////////////////////////////////////////////////////////////

// Scala compiler options
Compile / scalacOptions ++= Seq(
  "-Xelide-below", "WARNING",       // Turn on optimizations with "WARNING" as the threshold
  "-feature",                       // Check feature warnings
  "-deprecation",                   // Check deprecation warnings
  "-Ywarn-unused:imports"           // Check for unused imports
)

/////////////////////////////////////////////////////////////////////////////
// Test-related Dependencies
/////////////////////////////////////////////////////////////////////////////

libraryDependencies ++= Seq(
  "org.scalamock" %% "scalamock" % "5.2.0" % Test,                  // ScalaMock
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,                 // ScalaTest
  "junit" % "junit" % "4.13.2" % Test,                              // JUnit
  "com.novocode" % "junit-interface" % "0.11" % Test                // SBT interface for JUnit
)


/////////////////////////////////////////////////////////////////////////////
// Jackson-related Dependencies
/////////////////////////////////////////////////////////////////////////////

val jacksonVersion = "2.17.2"
libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,                  // Jackson Databind
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,               // Jackson Annotation
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,           // Scala Module
)

// Lucene related
val luceneVersion = "8.7.0"
libraryDependencies ++= Seq(
  "org.apache.lucene" % "lucene-core" % luceneVersion,
  "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
  "org.apache.lucene" % "lucene-queries" % luceneVersion,
  "org.apache.lucene" % "lucene-memory" % luceneVersion
)

/////////////////////////////////////////////////////////////////////////////
// Additional Dependencies
/////////////////////////////////////////////////////////////////////////////

libraryDependencies ++= Seq(
  "org.jgrapht" % "jgrapht-core" % "1.4.0",                      // JGraphT Core
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.0.202109080827-r", // JGit
  "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0",
  // https://mvnrepository.com/artifact/org.apache.commons/commons-jcs3-core
  "org.apache.commons" % "commons-jcs3-core" % "3.2",
  "org.apache.commons" % "commons-lang3" % "3.14.0"
)