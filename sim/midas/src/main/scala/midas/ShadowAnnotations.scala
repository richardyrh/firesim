// See LICENSE for license details.

package midas

import chisel3._
import chisel3.experimental.{annotate, ChiselAnnotation}

import firrtl.transforms.DontTouchAllTargets
import firrtl.{AnnotationSeq, RenameMap}
import firrtl.annotations.{
  Annotation,
  ComponentName,
  HasSerializationHints,
  ModuleTarget,
  ReferenceTarget,
  SingleTargetAnnotation,
}

import midas.targetutils._
import midas.targetutils.xdc._

// annotations from targetutils that need's this version of FIRRTL's DontTouchAllTargets added to it
// midas should use these annotations

case class InternalGlobalResetCondition(target: ReferenceTarget)
    extends SingleTargetAnnotation[ReferenceTarget]
    with DontTouchAllTargets        {
  def duplicate(n: ReferenceTarget) = this.copy(target = n)
}
object InternalGlobalResetCondition {
  def apply(a: GlobalResetCondition): InternalGlobalResetCondition = {
    InternalGlobalResetCondition(a.target)
  }

  // a copy from targetutils that generates InternalGlobalResetCondition's

  /** Label a bool as the global reset condition.
    *
    * @param target
    *   The chisel Bool to be annotated.
    */
  def apply(target: Bool): Unit = {
    // Workaround: Prevent promote passthroughs from extracting the fanout
    // generated by wiring the GlobalResetCondition to sink bridges, by
    // introducing an identity logic function that it cannot "see" through.
    //
    // If we neglect this, the wiring pass may introduce combinational
    // dependency between channels in two different domains, which currently is
    // not checked for and will produce simulator deadlock.
    val condition = PassthroughBlocker(target)
    annotate(new ChiselAnnotation { def toFirrtl = InternalGlobalResetCondition(condition.toTarget) })
  }

  /** Label a bool that should be driven by the global reset condition.
    *
    * @param target
    *   The chisel Bool to be annotated.
    */
  def sink(target: Bool): Unit = annotate(new ChiselAnnotation {
    def toFirrtl = InternalGlobalResetConditionSink(target.toTarget)
  })

  /** Generates a bool that will be driven by the global reset condition.
    *
    * Due to pecularities with when GG can run the wiring transform, this method must be used to produce a wire if a
    * port cannot be annotated.
    */
  def produceSink(): Bool = {
    val globalResetConditionSinkModule = Module(new Module {
      val in  = IO(Input(Bool()))
      val out = IO(Output(Bool()))
      out := in
      InternalGlobalResetCondition.sink(out)
    })
    globalResetConditionSinkModule.in := false.B
    globalResetConditionSinkModule.out
  }
}

case class InternalGlobalResetConditionSink(target: ReferenceTarget)
    extends SingleTargetAnnotation[ReferenceTarget]
    with DontTouchAllTargets            {
  def duplicate(n: ReferenceTarget) = this.copy(target = n)
}
object InternalGlobalResetConditionSink {
  def apply(a: GlobalResetConditionSink): InternalGlobalResetConditionSink = {
    InternalGlobalResetConditionSink(a.target)
  }
}

case class InternalFirrtlFpgaDebugAnnotation(target: ComponentName)
    extends SingleTargetAnnotation[ComponentName]
    with DontTouchAllTargets             {
  def duplicate(n: ComponentName) = this.copy(target = n)
}
object InternalFirrtlFpgaDebugAnnotation {
  def apply(a: FirrtlFpgaDebugAnnotation): InternalFirrtlFpgaDebugAnnotation = {
    InternalFirrtlFpgaDebugAnnotation(a.target)
  }
}

case class InternalAutoCounterFirrtlAnnotation(
  target:         ReferenceTarget,
  clock:          ReferenceTarget,
  reset:          ReferenceTarget,
  label:          String,
  description:    String,
  opType:         PerfCounterOpType = PerfCounterOps.Accumulate,
  coverGenerated: Boolean           = false,
) extends Annotation
    with DontTouchAllTargets
    with HasSerializationHints             {
  def update(renames: RenameMap): Seq[Annotation] = {
    val renamer       = new ReferenceTargetRenamer(renames)
    val renamedTarget = renamer.exactRename(target)
    val renamedClock  = renamer.exactRename(clock)
    val renamedReset  = renamer.exactRename(reset)
    Seq(this.copy(target = renamedTarget, clock = renamedClock, reset = renamedReset))
  }
  // The AutoCounter tranform will reject this annotation if it's not enclosed
  def shouldBeIncluded(modList: Seq[String]): Boolean = !coverGenerated || modList.contains(target.module)
  def enclosingModule(): String             = target.module
  def enclosingModuleTarget(): ModuleTarget = ModuleTarget(target.circuit, enclosingModule())
  def typeHints: Seq[Class[_]]              = Seq(opType.getClass)
}
object InternalAutoCounterFirrtlAnnotation {
  def apply(a: AutoCounterFirrtlAnnotation): InternalAutoCounterFirrtlAnnotation = {
    InternalAutoCounterFirrtlAnnotation(a.target, a.clock, a.reset, a.label, a.description, a.opType, a.coverGenerated)
  }
}

case class InternalTriggerSourceAnnotation(
  target:     ReferenceTarget,
  clock:      ReferenceTarget,
  reset:      Option[ReferenceTarget],
  sourceType: Boolean,
) extends Annotation
    with FAMEAnnotation
    with DontTouchAllTargets           {
  def update(renames: RenameMap): Seq[firrtl.annotations.Annotation] = {
    val renamer       = new ReferenceTargetRenamer(renames)
    val renamedTarget = renamer.exactRename(target)
    val renamedClock  = renamer.exactRename(clock)
    val renamedReset  = reset.map(renamer.exactRename)
    Seq(this.copy(target = renamedTarget, clock = renamedClock, reset = renamedReset))
  }
  def enclosingModuleTarget(): ModuleTarget = ModuleTarget(target.circuit, target.module)
  def enclosingModule(): String             = target.module
}
object InternalTriggerSourceAnnotation {
  def apply(a: TriggerSourceAnnotation): InternalTriggerSourceAnnotation = {
    InternalTriggerSourceAnnotation(a.target, a.clock, a.reset, a.sourceType)
  }
}

case class InternalTriggerSinkAnnotation(
  target: ReferenceTarget,
  clock:  ReferenceTarget,
) extends Annotation
    with FAMEAnnotation
    with DontTouchAllTargets         {
  def update(renames: RenameMap): Seq[firrtl.annotations.Annotation] = {
    val renamer       = new ReferenceTargetRenamer(renames)
    val renamedTarget = renamer.exactRename(target)
    val renamedClock  = renamer.exactRename(clock)
    Seq(this.copy(target = renamedTarget, clock = renamedClock))
  }
  def enclosingModuleTarget(): ModuleTarget = ModuleTarget(target.circuit, target.module)
}
object InternalTriggerSinkAnnotation {
  def apply(a: TriggerSinkAnnotation): InternalTriggerSinkAnnotation = {
    InternalTriggerSinkAnnotation(a.target, a.clock)
  }
}

case class InternalXDCAnnotation(
  destinationFile: XDCDestinationFile,
  formatString:    String,
  argumentList:    ReferenceTarget*
) extends Annotation
    with XDCAnnotationConstants
    with HasSerializationHints
    // This is included until we figure out how to gracefully handle deletion.
    with DontTouchAllTargets {
  def update(renames: RenameMap): Seq[Annotation] = {
    val renamer = new ReferenceTargetRenamer(renames)
    Seq(InternalXDCAnnotation(destinationFile, formatString, argumentList.map(a => renamer.exactRename(a)): _*))
  }
  def typeHints: Seq[Class[_]] = Seq(classOf[XDCDestinationFile])
}
object InternalXDCAnnotation {
  def apply(a: XDCAnnotation): InternalXDCAnnotation = {
    InternalXDCAnnotation(a.destinationFile, a.formatString, a.argumentList: _*)
  }
}

object ConvertExternalToInternalAnnotations {
  def apply(annotations: AnnotationSeq): AnnotationSeq = {
    annotations.map(a =>
      a match {
        case grc: GlobalResetCondition        => InternalGlobalResetCondition(grc)
        case grcs: GlobalResetConditionSink   => InternalGlobalResetConditionSink(grcs)
        case ffda: FirrtlFpgaDebugAnnotation  => InternalFirrtlFpgaDebugAnnotation(ffda)
        case afa: AutoCounterFirrtlAnnotation => InternalAutoCounterFirrtlAnnotation(afa)
        case tsrca: TriggerSourceAnnotation   => InternalTriggerSourceAnnotation(tsrca)
        case tsa: TriggerSinkAnnotation       => InternalTriggerSinkAnnotation(tsa)
        case xa: XDCAnnotation                => InternalXDCAnnotation(xa)
        case other                            => other
      }
    )
  }
}
