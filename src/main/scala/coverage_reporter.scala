package me.zhihan.jacoco

import me.zhihan.jacoco.internal.{Mapper, BranchExp, CovExp}
import org.jacoco.core.data.{ExecutionDataStore, ExecutionData}
import org.jacoco.core.internal.data.CRC64
import org.objectweb.asm.ClassReader
import scala.collection.mutable.Map

class CoverageReporter(val store: ExecutionDataStore) {
  val mapper = new Mapper()

  def analyzeClass(reader: ClassReader) {
    val classid = CRC64.checksum(reader.b)
    val execData: ExecutionData = store.get(classid)

    if (execData != null) {
      val probes = execData.getProbes()
      val lineToBranchExp: Map[Int, BranchExp] =
        mapper.analyzeClass(reader)
      lineToBranchExp.foreach { case (line: Int, branchExp: BranchExp) =>
        if (branchExp.branches.size > 1) {
          print(s"Line $line: ") 
          branchExp.branches.foreach { exp =>
            if (CovExp.evaluate (probes) (exp)) {
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
