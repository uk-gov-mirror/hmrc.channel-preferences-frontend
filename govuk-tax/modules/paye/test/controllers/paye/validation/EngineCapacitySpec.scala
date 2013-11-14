package controllers.paye.validation

import uk.gov.hmrc.common.BaseSpec
import models.paye.EngineCapacity

class EngineCapacitySpec extends BaseSpec {
      "EngineCapacity " should {
        "give an empty engine capacity " in {
          EngineCapacity.engineCapacityEmpty(None) shouldBe(true)
          EngineCapacity.engineCapacityEmpty(Some("none")) shouldBe(true)
          EngineCapacity.engineCapacityEmpty("none") shouldBe(true)
        }
        "give a non-empty engine capacity " in {
          EngineCapacity.engineCapacityEmpty(Some("123")) shouldBe(false)
          EngineCapacity.engineCapacityEmpty("456") shouldBe(false)
        }

        "map an empty engine capacity to None " in {
          EngineCapacity.mapEngineCapacityToInt(Some("none")) shouldBe None
          EngineCapacity.mapEngineCapacityToInt(None) shouldBe None

        }

        "map a non-empty engine capacity to the same value " in {
          EngineCapacity.mapEngineCapacityToInt(Some("123")) shouldBe Some(123)
        }
      }
}
