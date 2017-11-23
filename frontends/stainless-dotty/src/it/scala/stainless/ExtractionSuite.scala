/* Copyright 2009-2016 EPFL, Lausanne */

package stainless

import scala.util.{ Try, Success, Failure }

import org.scalatest._

class ExtractionSuite extends FunSpec with inox.ResourceUtils with InputUtils {


  private def testSetUp(dir: String): (inox.Context, List[String]) = {
    val ctx = stainless.TestContext.empty
    val fs = resourceFiles(dir, _.endsWith(".scala")).toList map { _.getPath }
    (ctx, fs)
  }

  def testExtractAll(dir: String): Unit = {
    val (ctx, files) = testSetUp(dir)
    import ctx.reporter

    describe(s"Program extraction in $dir") {
      val tryProgram = scala.util.Try(loadFiles(ctx, files)._2)
      it("should be successful") { assert(tryProgram.isSuccess) }

      if (tryProgram.isSuccess) {
        val program = tryProgram.get

        it("should typecheck") {
          program.symbols.ensureWellFormed
          for (fd <- program.symbols.functions.values.toSeq) {
            import program.symbols._
            assert(isSubtypeOf(fd.fullBody.getType, fd.returnType))
          }
        }

        val tryExProgram = Try(extraction.extract(program, ctx))
        describe("and transformation") {
          it("should be successful") { assert(tryExProgram.isSuccess) }

          if (tryExProgram.isSuccess) {
            val exProgram = tryExProgram.get
            it("should produce no errors") { assert(reporter.errorCount == 0) }

            it("should typecheck") {
              exProgram.symbols.ensureWellFormed
              for (fd <- exProgram.symbols.functions.values.toSeq) {
                import exProgram.symbols._
                assert(isSubtypeOf(fd.fullBody.getType, fd.returnType))
              }
            }

            it("should typecheck without matches") {
              for (fd <- exProgram.symbols.functions.values.toSeq) {
                import exProgram.symbols._
                assert(isSubtypeOf(matchToIfThenElse(fd.fullBody).getType, fd.returnType))
              }
            }
          }
        }
      }
    }
  }

  def testRejectAll(dir: String): Unit = {
    val (ctx, files) = testSetUp(dir)
    import ctx.reporter

    describe(s"Programs extraction in $dir") {
      val tryPrograms = files map { f => f -> Try(loadFiles(ctx, List(f))._2) }
      it("should fail") {
        tryPrograms foreach { case (f, tp) => tp match {
          // we expect a specific kind of exception:
          case Failure(e: stainless.frontend.UnsupportedCodeException) => assert(true)
          case Failure(e) => assert(false, s"$f was rejected with $e")
          case Success(_) => assert(false, s"$f was successfully extracted")
        }}
      }
    }
  }

  testExtractAll("verification/valid")
  testExtractAll("verification/invalid")
  testExtractAll("verification/unchecked")
  testExtractAll("imperative/valid")
  testExtractAll("imperative/invalid")
  testExtractAll("termination/valid")
  testExtractAll("termination/looping")
  testExtractAll("extraction/valid")
  testRejectAll("extraction/invalid")
}
