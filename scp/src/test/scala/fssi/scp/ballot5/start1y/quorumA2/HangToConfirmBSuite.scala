package fssi.scp.ballot5.start1y.quorumA2

import fssi.scp.ballot5.start1y.steps.QuorumA2
import org.scalatest.Matchers._

class HangToConfirmBSuite extends QuorumA2 {
  override def suiteName = "Hang - does not switch to B in CONFIRM"

  override def beforeEach(): Unit = {
    super.beforeEach()

    start1Y()
    preparedA1()
    preparedA2()
    confirmPreparedA2()
    acceptCommit()
    quorumA2()
  }

  test("Network EXTERNALIZE") {
    onEnvelopesFromVBlocking(makeExternalizeGen(B2, hn = 3))

    app.numberOfEnvelopes shouldBe 7
    app.shouldHaveConfirmed(pn = 2, AInf, cn = 2, hn = 2)
    app.shouldNotHaveBallotTimer()

    // stuck
    onEnvelopesFromQuorumChecks(makeExternalizeGen(B2, hn = 3), checkEnvelopes = false, isQuorumDelayed = false)

    app.numberOfEnvelopes shouldBe 7
    app.numberOfExternalizedValues shouldBe 0
    // timer scheduled as there is a quorum
    // with (inf, *)
    app.shouldBallotTimerUpcoming()
  }

  test("Network CONFIRMS other ballot at same counter") {
    // nothing should happen here, in
    // particular, node should not attempt
    // to switch 'p'
    onEnvelopesFromQuorumChecks(makeConfirmGen(pn = 3, B2, cn = 2, hn =3), checkEnvelopes = false, isQuorumDelayed = false)

    app.numberOfEnvelopes shouldBe 6
    app.numberOfExternalizedValues shouldBe 0
    app.shouldBallotTimerFallBehind()
  }

  test("Network CONFIRMS other ballot at a different counter") {
    onEnvelopesFromVBlocking(makeConfirmGen(pn = 3, B3, cn = 3, hn =3))

    app.numberOfEnvelopes shouldBe 7
    app.shouldHaveConfirmed(pn = 2, A3, cn = 2, hn = 2)
    app.shouldNotHaveBallotTimer()

    onEnvelopesFromQuorumChecks(makeConfirmGen(pn = 3, B3, cn = 3, hn = 3), checkEnvelopes = false, isQuorumDelayed = false)

    app.numberOfEnvelopes shouldBe 7
    app.numberOfExternalizedValues shouldBe 0
    // timer scheduled as there is a quorum
    // with (3, *)
    app.shouldBallotTimerUpcoming()
  }
}
