package unitTests

import config.AppConfig
import monix.eval.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AppConfigSpec extends AnyWordSpec with Matchers {
  "AppConfig" should {
    "be properly parsed" in {
      noException should be thrownBy {
        AppConfig.load[Task]
      }
    }
  }
}
