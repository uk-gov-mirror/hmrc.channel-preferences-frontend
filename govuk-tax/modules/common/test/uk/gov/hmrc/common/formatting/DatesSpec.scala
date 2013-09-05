package views.formatting

import org.scalatest.{ Matchers, WordSpec }
import Dates._
import org.joda.time.LocalDate

class DatesSpec extends WordSpec with Matchers {

  "Calling formatDate with a LocalDate object" should {

    "return the formatted date" in {
      val date = new LocalDate(2010, 9, 22)
      val expected = dateFormat.print(date)
      formatDate(date) should equal(expected)
    }
  }

  "Calling formatDate with an Optional LocalDate object and a default" should {

    "format the date if the input is Some date" in {
      val date = Some(new LocalDate(1984, 3, 31))
      val expected = dateFormat.print(date.get)
      formatDate(date, "the default value") should equal(expected)
    }

    "return the default if the input is None" in {
      val date = None
      val expected = "the default value"
      formatDate(date, "the default value") should equal(expected)
    }

  }
}
