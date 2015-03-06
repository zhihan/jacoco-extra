package me.zhihan.jacoco

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.SystemPropertiesRuntime;
import org.jacoco.core.runtime.RuntimeData;

import java.io.InputStream;
import java.io.PrintStream;

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileSystem
import java.nio.file.FileSystems

import scala.collection.mutable.Map
import scala.collection.JavaConversions._

object Helper {

  /**
    * An in-memory class loader that are used to load instrumented classes.
    */
  class MemoryClassLoader(parent:ClassLoader) extends ClassLoader(parent) {
    val definitions = Map[String, Array[Byte]]()

    /**
      * Add a in-memory representation of a class.
      * 
      * @param name
      *            name of the class
      * @param bytes
      *            class definition
      */
    def addDefinition(name:String, bytes:Array[Byte]) {
      definitions(name) = bytes
    }

    def isDefined(name:String) = definitions.contains(name)

    override protected def loadClass(name:String, resolve:Boolean): Class[_] = {
      val bytes = definitions.get(name);
      bytes match {
        case Some(b) => defineClass(name, b, 0, b.length)
        case None => {
          super.loadClass(name, resolve)
        }
      }
    }
  }

  private def getTargetClass(name: String): InputStream = {
    val resource = "/" + name.replace(".", "/") + ".class"
    getClass().getResourceAsStream(resource)
  }

  /** Initialze the objects*/
  val classLoader = new MemoryClassLoader(getClass().getClassLoader())
  val runtime = new SystemPropertiesRuntime()
  val instrumenter = new Instrumenter(runtime)

  /** Use the instrumenter to instrument the class and returns the byte code*/
  def instrument(name:String): Array[Byte] = 
    instrumenter.instrument(getTargetClass(name), name)

  /** Add the definition to the in-memory class loader */
  def addDefinition(name:String, bytes:Array[Byte]) {
    classLoader.addDefinition(name, bytes)
  }

  def newInstance(name:String) = {
    val clazz = classLoader.loadClass(name)
    clazz.newInstance()
  }

  def start: RuntimeData = {
    val data = new RuntimeData()
    runtime.startup(data)
    data
  }

  def shutdown {
    runtime.shutdown
  }

  def writeClassFile(bytes:Array[Byte], name:String) {
    val path = FileSystems.getDefault().getPath(name + ".class")
    Files.write(path, bytes)
  }

}
