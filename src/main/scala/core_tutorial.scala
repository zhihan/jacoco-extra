package org.jacoco.examples

import java.io.InputStream;
import java.io.PrintStream;

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

import org.objectweb.asm.ClassReader;

import scala.collection.mutable.Map
import scala.collection.JavaConversions._

abstract class MyInt2I {
  def getI(): Int
}


// Example from www.eclemma.org to demonstrate Jacoco's core API
object CoreTutorial {
  class MyInt2 extends MyInt2I {
    val i = 0
    def getI() = 1
  }

  class TestTarget extends Runnable {
    def run() {
      isPrime(7);
    }

    def isPrime(n:Int) = {
      var i = 2
      var result = false
      while (i*i <=n && !result) {
        if ((n ^ i ) == 0) {
          result = true
        }
        i += 1
      }
      result
    }
  }



  class MemoryClassLoader(cl:ClassLoader) extends ClassLoader(cl) {
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

    override protected def loadClass(name:String, resolve:Boolean) = {
      val bytes = definitions.get(name);
      bytes match {
        case Some(b) => defineClass(name, b, 0, b.length)
        case None => super.loadClass(name, resolve)
      }
    }
  }

  val out = System.out

  def execute {
    // Create instrumented byte code

    val classLoader = getClass().getClassLoader()
    classLoader.loadClass("org.jacoco.examples.MyInt2I")

    val targetName = classOf[MyInt2].getName()
    val runtime = new LoggerRuntime()
    val instrumenter = new Instrumenter(runtime)
    val instrumented = instrumenter.instrument(getTargetClass(targetName), targetName)

    // Start the runtime
    val data = new RuntimeData()
    runtime.startup(data)


    // Load the instrumented bytecode
    val memoryClassLoader = new MemoryClassLoader(classLoader)
    //val inter = new ClassReader(getTargetClass(classOf[MyIntI].getName()))
    //memoryClassLoader.addDefinition("org.jacoco.examples.MyIntI", inter.b)
    memoryClassLoader.addDefinition(targetName, instrumented)
    val targetClass = memoryClassLoader.loadClass(targetName)

    val targetInstance = targetClass.newInstance().asInstanceOf[MyInt2I]
    targetInstance.getI()

    // Run the code
    val executionData = new ExecutionDataStore()
    val sessionInfos = new SessionInfoStore()
    data.collect(executionData, sessionInfos, false)
    runtime.shutdown()

    // Together with the original class definition we can calculate coverage
    // information:
    val coverageBuilder = new CoverageBuilder()
    val analyzer = new Analyzer(executionData, coverageBuilder)
    analyzer.analyzeClass(getTargetClass(targetName), targetName)

    // Let's dump some metrics and line coverage information:

    coverageBuilder.getClasses().foreach { cc =>
      out.printf("Coverage of class %s%n", cc.getName());

      printCounter("instructions", cc.getInstructionCounter());
      printCounter("branches", cc.getBranchCounter());
      printCounter("lines", cc.getLineCounter());
      printCounter("methods", cc.getMethodCounter());
      printCounter("complexity", cc.getComplexityCounter());

      var lineIdx = 0
      for (lineIdx <- cc.getFirstLine() to cc.getLastLine()) {
        val line = cc.getLine(lineIdx)
        val bc = line.getBranchCounter()
        if (bc.getTotalCount() > 0) {
          out.printf("Branch at %s: Covered:%s, Total:%s %n",
            Integer.valueOf(lineIdx),
            Integer.valueOf(bc.getCoveredCount()),
            Integer.valueOf(bc.getTotalCount()))
        }
      }

    }



  }

  def getTargetClass(name:String): InputStream = {
    val resource = "/" + name.replace(".", "/") + ".class"
    getClass().getResourceAsStream(resource)
  }

  def printCounter(unit:String, counter:ICounter) {
    val missed = Integer.valueOf(counter.getMissedCount());
    val total = Integer.valueOf(counter.getTotalCount());
    out.printf("%s of %s %s missed%n", missed, total, unit);
  }

}
