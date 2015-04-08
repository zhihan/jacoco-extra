package me.zhihan.jacoco

import me.zhihan.jacoco.internal.Mapper
import org.jacoco.core.data.{ExecutionDataStore, ExecutionData}
import org.jacoco.core.internal.data.CRC64
import org.objectweb.asm.ClassReader
import scala.collection.mutable.{Map, MultiMap, HashMap, Set}

class CoverageReporter(val store: ExecutionDataStore) {
  val mapper = new Mapper()

  def analyzeClass(reader: ClassReader) {
    val classid = CRC64.checksum(reader.b)
    val execData: ExecutionData = store.get(classid)

    if (execData != null) {
      val probes = execData.getProbes()
      val lineToProbes: MultiMap[Int, Int] =
        mapper.analyzeClass(reader)
      lineToProbes.foreach { case (line: Int, branches: Set[Int]) =>
        if (branches.size > 1) {
          print(s"Line $line: ") 
          branches.foreach { branch =>
            if (probes(branch)) {
              print("T")
            } else {
              print("F")
            }
          }
          println("")
        }
      }
    }
  }
}
