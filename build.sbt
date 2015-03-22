lazy val root = (project in file(".")).
  settings(
    name := "scala-jacoco",
    version := "0.1.0",
    scalaVersion := "2.11.5",
    libraryDependencies += "org.ow2.asm" % "asm" % "5.0.3",
    libraryDependencies += "org.jacoco" % "org.jacoco.core" % "0.7.4.201502262128",
    libraryDependencies += "com.google.guava" % "guava" % "18.0",
    libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.1.7" % "test",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v") 
  )


