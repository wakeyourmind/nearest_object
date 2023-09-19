import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import monix.execution.Scheduler
import service.JobF

object Job extends TaskApp {
  private implicit val monixScheduler: Scheduler = Scheduler.global
  override def run(args: List[String]): Task[ExitCode] = {
    JobF[Task].run
  }
}
