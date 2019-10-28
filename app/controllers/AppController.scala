package controllers

import javax.inject.Inject

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson._
import models.DAOExec
import models.DateUtils
import models.CustomException

import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json._
import play.api.mvc.MultipartFormData
import java.nio.file.{Files, Paths}
import play.api.libs.Files.TemporaryFile

import play.api.libs.oauth.ConsumerKey
import play.api.libs.oauth.ServiceInfo
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.OAuthCalculator

import play.api.libs.ws._
import play.api.Play.current

object URL {
  val media_root = "C:\\Users\\Diego Fernando\\Programação\\scala\\idd-project\\tmp\\picture\\"
  val url_root = "/files/"
}

class AppController @Inject()(val reactiveMongoApi: ReactiveMongoApi) extends Controller
    with MongoController with ReactiveMongoComponents {

  import controllers.User._

  val ck1 : String = current.configuration.getString("twitter.ck1").getOrElse("default value")
  val ck2 : String = current.configuration.getString("twitter.ck2").getOrElse("default value")

  val KEY = ConsumerKey(ck1, ck2)

  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY),
    true)

  /* Autenticando os usuários pelo Twitter */
  def authenticate = Action { request =>
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      println(tokenPair)
      println(verifier)
      // We got the verifier; now get the access token, store it and back to index
      TWITTER.retrieveAccessToken(tokenPair, verifier) match {
        case Right(t) => {
          // We received the authorized tokens in the OAuth object - store it before we proceed
          Redirect(routes.AppController.callbackTwitter).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      }
    }.getOrElse(
      TWITTER.retrieveRequestToken("http://localhost:9000/api/auth") match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => throw e
      })
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  /* Consultando usuário do Twitter pelo token e sanvando suas informações no banco */
  def callbackTwitter = Action.async { implicit request =>

    Twitter.sessionTokenPair match {
      case Some(credentials) => {
        WS.url("https://api.twitter.com/1.1/account/verify_credentials.json")
          .sign(OAuthCalculator(KEY, credentials))
          .get
          .map{
              response =>
              val name = (response.json \ "name").as[String]
              val image = (response.json \ "profile_image_url").as[String]
              val twitter = (response.json \ "screen_name").as[String]
              println(image)
              val r = scala.util.Random
              val randomCpfString = r.nextInt(999999999).toString
              val cpf = randomCpfString + "00"
              val rg = r.nextInt(9999999).toString

              dao.save(BSONDocument(
                Name -> name,
                TwitterUser -> twitter,
                CPF -> cpf,
                RG -> rg,
                Date_birth -> DateUtils.currentData(),
                Mother_name -> "...",
                Father_name -> "...",
                Date_joined -> DateUtils.currentData(),
                Pictures -> BSONArray(
                    BSONDocument("uploadAt" -> DateUtils.currentData(), "url" -> image)
                  )
                

              )).map{wr: WriteResult =>
                  if (wr.ok && wr.n == 1) {
                    Ok("Successful adding via Twitter with user: " + twitter)    
                  } else {
                    Status(400)("Error: Unidentified error")    
                  }
                }.recover{ case t: Throwable =>
                  Status(400)("Error: 'CPF' or 'RG' already registered")
                }
            Ok("Successful adding via Twitter with user: " + twitter)     
          }
      }
      case _ => Future.successful(Redirect(routes.AppController.authenticate))
    }
  }

  /* Listando todos os usuários */
  def list = Action.async { implicit request =>
    dao.find().map(widgets => Ok(Json.toJson(widgets)))
  }

  /* Serve os arquivos de imagens */
  def file(filename: String) = Action {
    val path = URL.media_root + filename
    Ok.sendFile(new java.io.File(path))
  }

  /* Adiciona uma foto para o usuário (deve estar no formato '.jpg') */
  def update(digit: String) = Action.async(parse.multipartFormData) { request =>
    try { 
      request.body.file("picture").map { picture =>
        import java.io.File
        val filename = picture.filename
        val contentType = picture.contentType

        if (filename.split('.').last.toLowerCase != "jpg"){
          throw CustomException("optional")
        }

        val r = scala.util.Random
        val randomString = r.nextInt(9999).toString
        val newNamefile = digit + randomString + ".jpg"

        picture.ref.moveTo(
          new File(
            s"C:\\Users\\Diego Fernando\\Programação\\scala\\idd-project\\tmp\\picture\\$newNamefile"
          )
        )
        
        dao.update(
          BSONDocument(
            "$or"-> BSONArray(
              List(
                BSONDocument(RG -> digit), BSONDocument(CPF -> digit)
              )
            )
          ),
          BSONDocument(
            "$push" -> BSONDocument(
              Pictures -> BSONDocument("uploadAt" -> DateUtils.currentData(), "url" -> BSONString(URL.url_root + newNamefile))
            )
          )
        ).map{
          wr: WriteResult =>
          if (wr.ok && wr.n == 1) {
            Ok("Successful adding")    
          } else {
            Status(400)("Error: Unidentified error")    
          }
        }.recover{ 
          case t: Throwable =>
            Status(400)("Error: Unidentified error")
          }
        }
          
      .getOrElse {
        Future.successful(
          Status(400)("Error: Unidentified error")
        )
      }
    } catch {
      case e:Exception=>
        Future.successful(
          Status(400)("Error: Unidentified error")
        )
    }
  }

  /* Adiciona um novo usuário */
  def add = Action.async(BodyParsers.parse.json) { implicit request =>
    val name = (request.body \ Name).as[String]
    val cpf = (request.body \ CPF).as[String]
    val rg = (request.body \ RG).as[String]
    val date_birth = (request.body \ Date_birth).as[String]
    val mother_name = (request.body \ Mother_name).as[String]
    val father_name = (request.body \ Father_name).as[String]
    try {
      if (name == "" || cpf == "" || rg == "" || mother_name == ""){
        throw CustomException("optional")
      }
      val date_birth_formated = DateUtils.convertStringToDate(date_birth)

      dao.save(BSONDocument(
      Name -> name,
      TwitterUser -> "",
      CPF -> cpf,
      RG -> rg,
      Date_birth -> date_birth_formated,
      Mother_name -> mother_name,
      Father_name -> father_name,
      Date_joined -> DateUtils.currentData(),
      Pictures -> BSONArray()

    )).map{wr: WriteResult =>
        if (wr.ok && wr.n == 1) {
          Ok("Successful adding")    
        } else {
          Status(400)("Error: Unidentified error")    
        }
      }.recover{ case t: Throwable =>
        Status(400)("Error: 'CPF' or 'RG' already registered")
      } 
        
    } catch {
      case e:Exception=>
        Future.successful(
          Status(400)("Error: Unidentified error")
        )
    }
    

  }

  /* Retorna um usuário */
  def read(digit: String) = Action.async { implicit request =>
    dao.select(
      BSONDocument(
        "$or"-> BSONArray(
          List(
            BSONDocument(RG -> digit), BSONDocument(CPF -> digit)
          )
        )
      )
    ).map(widget => Ok(Json.toJson(widget)))
  }

  /* Busca usuários pelo nome */
  def search(name: String) = Action.async { implicit request =>
    dao.search_users(
      BSONDocument(
        Name -> BSONDocument("$regex" -> (".*" + name + ".*"))
      )
    ).map(widget => Ok(Json.toJson(widget)))
  }

  /* Deleta um usuário */
  def delete(digit: String) = Action.async {
    dao.remove(
      BSONDocument(
        "$or"-> BSONArray(
          List(
            BSONDocument(RG -> digit), BSONDocument(CPF -> digit)
          )
        )
      )
    ).map(result => Accepted)
  }

  def dao = new DAOExec(reactiveMongoApi)
}

object Twitter {
  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }
}

object User {
  val Id = "_id"
  val Name = "name"
  val TwitterUser = "twitter"
  val CPF = "cpf"
  val RG = "rg"
  val Date_birth = "date_birth"
  val Mother_name = "mother_name"
  val Father_name = "father_name"
  val Date_joined = "date_joined"
  val Pictures = "pictures"
}

