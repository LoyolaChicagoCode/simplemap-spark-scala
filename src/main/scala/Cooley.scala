/*
 * SimpleMap: benchmark that shows how to work with binary data files and perform an inplace vector
 * shift. This uses Breeze DenseVector and stays in vector as long as possible until a DenseMatrix is
 * actually needed.
 */

package edu.luc.cs

import scala.util.{ Try, Success, Failure }
import java.io._

object GenerateBashScripts {

  val cores = 12

  def main(args: Array[String]) {
    val scripts = generate()
    scripts foreach println
  }

  def text(nodes: Int, nparts: Int, blocks: Int, blockSize: Int): String =
    s"$nodes $nparts $blocks $blockSize"

  def generate(): Iterator[(String, String)] = {
    for {
      nodes <- List(1, 4, 8, 16, 32, 64, 120).iterator
      nparts <- List(nodes * cores)
      blocks <- List(1000, 2000)
      blockSize <- List(10, 100, 1000)
    } yield {
      val NAME = s"do-simplemap-$nodes-$nparts-$blocks-$blockSize.sh"

      val HEADER = s"""#!/bin/bash
      |# Generated by cooley.py.
      |# Edit cooley_include.py and re-run cooley.py to generate instnaces.
      |#
      |# Parameters:
      |#
      |# nodes = $nodes
      |# nparts = $nparts
      |# blocks = $blocks
      |# blockSize = $blockSize
      |# cores = $cores
      |#""".stripMargin

      val START_SPARK = s"""
      |#
      |# Start Apache Spark
      |#
      |
      |JOB_LOG=$$HOME/logs/$$COBALT_JOBID.txt
      |
      |pushd $$HOME/code/spark
      |cat $$COBALT_NODEFILE > conf/slaves
      |cat $$COBALT_NODEFILE >> $$JOB_LOG
      |./sbin/start-all.sh
      |NODES=`wc -l conf/slaves | cut -d" " -f1`
      |popd
      |
      |MASTER=`hostname`
      |
      |echo "# Spark is now running with $$NODES workers:" >> $$JOB_LOG
      |echo "#"
      |echo "export SPARK_STATUS_URL=http://$$MASTER.cooley.pub.alcf.anl.gov:8000" >> $$JOB_LOG
      |echo "export SPARK_MASTER_URI=spark://$$MASTER:7077" >> $$JOB_LOG
      |
      |SPARK_MASTER_URI=spark://$$MASTER:7077
      |SPARK_HOME=$$HOME/code/spark
      |
      |#
      |# Done Initializing Apache Spark
      |#""".stripMargin

      val SUBMIT_SPARK = s"""
      |#
      |# Submit Application on Spark
      |#
      |
      |ASSEMBLY=target/scala-2.10/simplemap-spark-scala-assembly-1.0.jar
      |if [ -f "$$ASSEMBLY" ]; then
      |
      |   echo "Running: "$$SPARK_HOME/bin/spark-submit \\
      |      --master $$SPARK_MASTER_URI $$ASSEMBLY \\
      |      --blocks $blocks --block_size $blockSize --nodes $nodes \\
      |      --nparts $nparts --cores $cores >> $$JOB_LOG
      |
      |   $$SPARK_HOME/bin/spark-submit \\
      |      --master $$SPARK_MASTER_URI $$ASSEMBLY \\
      |      --blocks $blocks --block_size $blockSize --nodes $nodes \\
      |      --nparts $nparts --cores $cores >> $$JOB_LOG
      |      --dim %(dim)s --nodes %(nodes)s --partitions %(partitions)s --workload %(workload)s --outputdir /home/thiruvat/logs --json --xml >> $$JOB_LOG
      |else
      |   echo "Could not find Scala target assembly. No experiments run." >> $$JOB_LOG
      |fi
      |
      |#
      |# Done Submitting Application on Spark
      |#
      |""".stripMargin

      (NAME, HEADER + START_SPARK + SUBMIT_SPARK)
    }
  }
}
