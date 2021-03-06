/* Copyright 2009-2016 EPFL, Lausanne */

package stainless
package termination

import inox.utils.ASCIIHelpers.{ Cell, Row }
import stainless.utils.JsonConvertions._

import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._

import scala.util.{ Right, Left }

object TerminationReport {

  sealed abstract class Status {
    def isUnknown = this == Unknown
    def isTerminating = this == Terminating
    def isNonTerminating = this == NonTerminating
  }

  case object Unknown extends Status
  case object Terminating extends Status
  case object NonTerminating extends Status

  implicit val statusDecoder: Decoder[Status] = deriveDecoder
  implicit val statusEncoder: Encoder[Status] = deriveEncoder

  case class Record(
    id: Identifier, pos: inox.utils.Position, time: Long,
    status: Status, verdict: String, kind: String,
    generation: Long = 0 // "age" of the record, usefull to determine which ones are "NEW".
  ) extends AbstractReportHelper.Record

  implicit val recordDecoder: Decoder[Record] = deriveDecoder
  implicit val recordEncoder: Encoder[Record] = deriveEncoder

  def parse(json: Json): TerminationReport = json.as[(Seq[Record], Long)] match {
    case Right((records, lastGen)) => new TerminationReport(records, lastGen + 1)
    case Left(error) => throw error
  }

}

// Variant of the report without the checker, where all the data is mapped to text
class TerminationReport(val results: Seq[TerminationReport.Record], lastGen: Long = 0)
  extends AbstractReport[TerminationReport] {
  import TerminationReport._

  override val name: String = TerminationComponent.name

  lazy val totalValid = results count { _.status.isTerminating }
  lazy val totalValidFromCache = 0
  lazy val totalInvalid = results count { _.status.isNonTerminating }
  lazy val totalUnknown = results count { _.status.isUnknown }
  lazy val totalTime = (results map { _.time }).sum

  override def isSuccess = totalUnknown + totalInvalid == 0

  override def ~(other: TerminationReport) = {
    def updater(nextGen: Long)(r: Record) = r.copy(generation = nextGen)
    val (fused, nextGen) = AbstractReportHelper.merge(this.results, other.results, lastGen, updater)
    new TerminationReport(fused, nextGen)
  }

  override def filter(ids: Set[Identifier]) = {
    val (filtered, nextGen) = AbstractReportHelper.filter(results, ids, lastGen)
    new TerminationReport(filtered, nextGen)
  }

  override def emitRowsAndStats: Option[(Seq[Row], ReportStats)] = if (results.isEmpty) None else {
    val rows = for { Record(id, pos, time, status, verdict, kind, gen) <- results } yield Row(Seq(
      Cell(if (gen == lastGen) "NEW" else ""),
      Cell(id.name),
      Cell((if (status.isTerminating) "\u2713" else "\u2717") + " " + verdict),
      Cell(f"${time / 1000d}%3.3f")
    ))

    val stats = ReportStats(results.size, totalTime, totalValid, totalValidFromCache, totalInvalid, totalUnknown)

    Some((rows, stats))
  }

  override def emitJson: Json = (results, lastGen).asJson

}

