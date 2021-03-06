/* Copyright 2009-2016 EPFL, Lausanne */

package stainless
package verification

trait VerificationAnalysis extends AbstractAnalysis {
  val program: Program { val trees: stainless.trees.type }
  val results: Map[VC[program.trees.type], VCResult[program.Model]]

  lazy val vrs: Seq[(VC[stainless.trees.type], VCResult[program.Model])] =
    results.toSeq.sortBy { case (vc, _) => (vc.fd.name, vc.kind.toString) }

  override val name = VerificationComponent.name

  override type Report = VerificationReport

  override def toReport = new VerificationReport(vrs map { case (vc, vr) =>
    val time = vr.time.getOrElse(0L) // TODO make time mandatory (?)
    val status = VerificationReport.Status(vr.status)
    val solverName = vr.solver map { _.name }
    VerificationReport.Record(vc.fd, vc.getPos, time, status, solverName, vc.kind.name)
  })
}

