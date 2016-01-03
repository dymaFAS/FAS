package models.db

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import org.joda.time.DateTime
import models.db.Tables.Accounttype
import models.db.Tables.Country
import models.db.Tables.Quote
import models.db.Tables.Rate
import models.db.Tables.RateRow
import models.db.Tables.User
import models.db.Tables.UserRow
import play.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.cache.Cache
import slick.driver.PostgresDriver.api._
import scala.util.Success
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import models.db.Tables.Registration
import models.db.Tables.RegistrationRow

object defaultDb {

  def uuid = java.util.UUID.randomUUID.toString

  def preRegisterUser(email: String, password: String) = {
    Logger.debug("preRegistering user: " + email)
    val myDb = Database.forConfig("mydb")
    val today = new java.sql.Timestamp(DateTime.now().getMillis)
    val regQueries: TableQuery[Registration] = TableQuery[Registration]
    val uid = uuid
    val newIn: RegistrationRow = new RegistrationRow(uid, email, password, today,Some(false))
    val updateAction: DBIO[Int] = regQueries.insertOrUpdate(newIn)
    val ret = myDb.run(updateAction)
    val tadsf = Await.result(ret, Duration.Inf)
    uid
  }

  def loginAndSaveUser(email: String, password: Option[String], lastLogin: Option[String]): Future[Int] = {
    val myDb = Database.forConfig("mydb")
    Logger.debug("getting user: {}", email)
    var rt: Future[Int] = null
    val users = for (u <- User) yield u
    val u = users.filter { _.email === email }
    val query = myDb.run(u.result)
    query.onComplete({
      case Success(ret) => {
        Logger.debug(ret.toString())
      }
      case Failure(exception) => {
        Logger.debug("db user query failed " + exception.toString())
        rt = Future {
          0
        }
      }
    })
    val t = Await.result(query, Duration.Inf)

    val user = t.headOption
    if (user.isDefined) {
      val knownUser = user.get
      Logger.debug("user found id is: {}", knownUser.id.toString())
      val today = new java.sql.Timestamp(DateTime.now().getMillis)
      val userQueries: TableQuery[User] = TableQuery[User]
      val newIn: UserRow = new UserRow(knownUser.id, email, password, today, Some(false), lastLogin)
      val updateAction: DBIO[Int] = userQueries.insertOrUpdate(newIn)
      rt = myDb.run(updateAction)
      Logger.debug(updateAction.toString())
    } else {
      Logger.debug("user not found, inserting")
      val today = new java.sql.Timestamp(DateTime.now().getMillis)
      val userQueries: TableQuery[User] = TableQuery[User]
      val newUser: UserRow = new UserRow(100, email, password, today, Some(false), lastLogin)
      val insertAction: DBIO[Int] = (userQueries returning userQueries.map(x => x.id)) += newUser
      rt = myDb.run(insertAction)
    }
    rt onSuccess {
      case ret =>
        {
          Logger.debug("save user return: " + ret)
          myDb.close()
        }
    }

    rt
  }

  def getUserRegistration(confirmId: String) = {

  }

  def getQuotes = {
    val db = Database.forConfig("mydb")
    try {
      Logger.debug("get quotes")
      val qus = for (q <- Quote) yield q
      db.run(qus.result)
    } finally {
      db.close()
    }
  }

  def getCachesQuotes: Future[Seq[Tables.QuoteRow]] = {

    Logger.debug("get cached quotes")
    if (Cache.get("quotes") == null) {
      val db = Database.forConfig("mydb")
      try {
        Logger.debug("querying db")
        val qus = for (q <- Quote) yield q

        val f = db.run(qus.result)
        f.onSuccess { case ret => Cache.set("quotes", ret) }
        f
      } finally {
        db.close()
      }
    } else {
      Logger.debug("querying cache")
      Future {
        Cache.get("quotes").asInstanceOf[Seq[Tables.QuoteRow]]
      }
    }

  }

  def ratesIsEmpty() = {
    val myDb = Database.forConfig("mydb")
    val rates: TableQuery[Rate] = TableQuery[Rate]
    !Await.result(myDb.run(rates.exists.result), Duration.Inf)
  }

  def lastRateDate() = {
    val myDb = Database.forConfig("mydb")
    val rates: TableQuery[Rate] = TableQuery[Rate]
    val q = rates.map { x => x.ondate }
    Await.result(myDb.run(q.max.result), Duration.Inf)
  }

  ////rates
  def saveRates(newRates: List[RateRow]) = {
    val myDb = Database.forConfig("mydb")
    Logger.debug(if (newRates.size > 0) "saving rates: ..." else "no new rates")
    val insertQuery: TableQuery[Rate] = TableQuery[Rate]
    val insertAction = insertQuery ++= newRates //*insertQuery returning insertQuery.map(x => x.id))

    val t = myDb.run(insertAction)
    t.onSuccess {
      case ret => {
        Logger.debug("rateId: {}", ret.toString())
      }
    }
    t.onFailure {
      case ret => {
        Logger.debug("rateFailure: {}", ret.toString())
      }
    }
    t
  }

}