// See LICENSE for license details.

package midas.targetutils

import chisel3.{fromBooleanToLiteral, Bool, Input, Module, Output}
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl.annotations.{ReferenceTarget, SingleTargetAnnotation}

/** Masks off assertions, printfs, and autocounter events when the target bool is deasserted.
  *
  * Users typically wish to disable many forms instrumentation (e.g., synthesized assertions and printfs, autocounters)
  * while the target is under reset. By default, most of FireSim's instrumentations features mask off events using a
  * module-local reset, generally Chisel's implicit reset. However, in some cases the module-local reset may not be
  * asserted at the start of a reset sequence, leading to spurious assertion fires. This annotation provides a
  * coarse-grained means to globally mask off these events until the target is sufficiently far in reset.
  *
  * Note, while it is possible to use this signal as a replacement for FireSim's trigger system, it is intended to
  * support this time-zero reset case, and has different timing semantics. See:
  * https://docs.fires.im/en/latest/Golden-Gate/Triggers.html
  *
  * @param target
  *   The boolean enable
  */

case class GlobalResetCondition(target: ReferenceTarget) extends SingleTargetAnnotation[ReferenceTarget] {
  def duplicate(n: ReferenceTarget) = this.copy(target = n)
}

/** Indicates that this boolean target should be driven by the globalResetCondition if it is available.
  */
case class GlobalResetConditionSink(target: ReferenceTarget) extends SingleTargetAnnotation[ReferenceTarget] {
  def duplicate(n: ReferenceTarget) = this.copy(target = n)
}

object GlobalResetCondition {

  /** Label a bool as the global reset condition.
    *
    * @param target
    *   The chisel Bool to be annotated.
    */
  def apply(target: chisel3.Bool): Unit = {
    // Workaround: Prevent promote passthroughs from extracting the fanout
    // generated by wiring the GlobalResetCondition to sink bridges, by
    // introducing an identity logic function that it cannot "see" through.
    //
    // If we neglect this, the wiring pass may introduce combinational
    // dependency between channels in two different domains, which currently is
    // not checked for and will produce simulator deadlock.
    val condition = PassthroughBlocker(target)
    annotate(new ChiselAnnotation { def toFirrtl = GlobalResetCondition(condition.toTarget) })
  }

  /** Label a bool that should be driven by the global reset condition.
    *
    * @param target
    *   The chisel Bool to be annotated.
    */
  def sink(target: chisel3.Bool): Unit = annotate(new ChiselAnnotation {
    def toFirrtl = GlobalResetConditionSink(target.toTarget)
  })

  /** Generates a bool that will be driven by the global reset condition.
    *
    * Due to pecularities with when GG can run the wiring transform, this method must be used to produce a wire if a
    * port cannot be annotated.
    */
  def produceSink(): chisel3.Bool = {
    val globalResetConditionSinkModule = Module(new Module {
      val in  = IO(Input(Bool()))
      val out = IO(Output(Bool()))
      out := in
      GlobalResetCondition.sink(out)
    })
    globalResetConditionSinkModule.in := false.B
    globalResetConditionSinkModule.out
  }
}
