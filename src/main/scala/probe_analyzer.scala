package me.zhihan.jacoco.internal

import org.jacoco.core.internal.flow.MethodProbesVisitor

/** 
  * CoverageAnalyzer is roughly an in-place replacement of
  * org.jacoco.core.analysis.Analyzer. It uses a different
  * implementation of the internal adapters to visit the classes.
  * 
  * The Jacoco internal relies on the fact that a method visitor would
  * visit the coverage nodes in the same order as they were created to
  * map the information correctly.
  *  
  */

class CoverageAnalyzer(
  val executionData: ExecutionDataStore) {



}
