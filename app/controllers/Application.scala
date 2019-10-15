package controllers

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.bson.BSONCountCommand.{ Count, CountResult }
import reactivemongo.api.commands.bson.BSONCountCommandImplicits._
import reactivemongo.bson.BSONDocument

import scala.concurrent.Future

@Singleton
class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller
    with MongoController with ReactiveMongoComponents {

  def jsonCollection = reactiveMongoApi.db.collection[JSONCollection]("widgets");
  def bsonCollection = reactiveMongoApi.db.collection[BSONCollection]("widgets");

  def index = Action {
    Logger.info("Application startup...")

      //  val Id = "_id"
  // val Name = "name"
  // val CPF = "cpf"
  // val RG = "rg"
  // val Date_birth = "date_birth"
  // val Mother_name = "mother_name"
  // val Father_name = "father_name"
  // val Date_joined = "date_joined"

    val users_list = List(
      Json.obj(
        "name" -> "Diego Fernando de Sousa Lima",
        "cpf" -> "06163732367",
        "rg" -> "3414055",
        "date_birth" -> "23/04/1996",
        "mother_name" -> "Irani Vieira de Sousa Lima",
        "father_name" -> "Rubens de Sousa Lima",
        "date_joined" -> "11/10/2019"
      ),
      Json.obj(
        "name" -> "Maria Aparecida de Lima",
        "cpf" -> "92839481923",
        "rg" -> "0983394",
        "date_birth" -> "24/02/1996",
        "mother_name" -> "Maria Josefa de Lima",
        "father_name" -> "João de Deus Lima",
        "date_joined" -> "11/10/2019"
      ))

    val query = BSONDocument("name" -> BSONDocument("$exists" -> true))
    val command = Count(query)
    val result: Future[CountResult] = bsonCollection.runCommand(command)

    result.map { res =>
      val numberOfDocs: Int = res.value
      if(numberOfDocs < 1) {
        jsonCollection.bulkInsert(users_list.toStream, ordered = true).foreach(i => Logger.info("Record added."))
      }
    }

    Ok("Your database is ready.")
  }

  def cleanup = Action {
    jsonCollection.drop().onComplete {
      case _ => Logger.info("Database collection dropped")
    }
    Ok("Your database is clean.")
  }
}
