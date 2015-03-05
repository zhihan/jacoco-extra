package me.zhihan.jacoco

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
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

  class MemoryClassLoader extends ClassLoader {
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

  def getTargetClass(name: String): InputStream = {
    val resource = "/" + name.replace(".", "/") + ".class"
    getClass().getResourceAsStream(resource)
  }

  val classLoader = new MemoryClassLoader()
  val runtime = new LoggerRuntime()
  val instrumenter = new Instrumenter(runtime)

  def instrument(name:String) = 
    instrumenter.instrument(getTargetClass(name), name)

  def addDefinition(name:String, bytes:Array[Byte]) {
    classLoader.addDefinition(name, bytes)
  }

  def loadClass(name:String) = 
    classLoader.loadClass(name)

  def newInstance(name:String) = {
    val clazz = classLoader.loadClass(name)
    clazz.newInstance()
  }

  def writeClassFile(bytes:Array[Byte], name:String) {
    val path = FileSystems.getDefault().getPath(name + ".class")
    Files.write(path, bytes)
  }
}
