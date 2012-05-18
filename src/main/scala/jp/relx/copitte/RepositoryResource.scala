package jp.relx.copitte

import java.io.File
import scala.reflect.BeanInfo
import org.slf4j.LoggerFactory
import javax.ws.rs.core.Response
import javax.ws.rs.DELETE
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import jp.relx.commons.CommandExecuteUtil.execCommand
import jp.relx.commons.CommandFailedException
import net.liftweb.json.DefaultFormats
import net.liftweb.json.parse
  
case class RepoInfo(vcs: String, name: String, pullurl: String, pushurl: String)
case class Author(name: String, email: String)
case class Commit(id: String, message: String, timestamp: String, url: String,
  added: String, removed: String, modified: String, author: Author)
case class Owner(name: String, email: String)
case class Repository(name: String, url: String, pledgie: String,
  description: String, homepage: String, watchers: Int, forks: Int,
  rprivate: Int, owner: Owner)
case class PostReceiveInfo(before: String, after: String, ref: String,
  commits: List[Commit], repository: Repository)

@Path("/repos")
class RepositoryResource {

  // TODO プロパティファイルに定義を移行する
  val OutPath = "/home/charles/copitte"
    
  // .git/config に書く remote の名前
  val PushRepoName = "pushRepo"

  
  val logger = LoggerFactory.getLogger(getClass) 
  
  /**
   * jsonのパースのフォーマット指定。暗黙的パラメータに使用される。
   */
  implicit val formats = DefaultFormats
  
  def getLocalRepoPath(repoName: String): String = OutPath + "/" + repoName
  
  @POST
  def registerRepo(bodyStr: String): Response = {
    try {
      logger.info(bodyStr)
      val repoInfo = parse(bodyStr).extract[RepoInfo]
      
      // TODO OutPath ディレクトリが空であるかチェックする
      val localRepoPath = getLocalRepoPath(repoInfo.name)
      
      gitClone() match {
        case (0, o, _) => logger.debug(o)
        case (_, _, e) => throw new CommandFailedException(e)
      }
      
      addRemoteConfig() match {
        case (0, o, _) => logger.debug(o)
        case (_, _, e) => throw new CommandFailedException(e)
      }
      
      /**
       * git clone します。
       * 
       * @param repoInfo      リポジトリ情報
       * @param localRepoPath クローンするディレクトリのパス
       * @return  (終了コード, 標準出力, 標準エラー出力)
       */
      def gitClone(): (Int, String, String) = 
        execCommand("git clone " + repoInfo.pullurl + " " + localRepoPath)

      /**
       * クローンしたリポジトリの設定に push 先リポジトリの情報を追加
       * git remote add [name] [pushurl] と同義
       */
      def addRemoteConfig(): (Int, String, String) = 
        execCommand(
          "git remote add " + repoInfo.name + " " + repoInfo.pushurl, 30 * 1000L,
          new File(localRepoPath)
        )
      
      val res =
        <html xmlns="http://www.w3.org/1999/xhtml">
          <body>Repogitory was registerd.</body>
        </html>
      Response.ok(res.toString()).build()
    } catch {
      case e: IllegalArgumentException => {
        logger.error(e.getMessage())
        Response.status(400).build()
      }
      case e: CommandFailedException => {
        logger.error(e.getMessage())
        Response.status(500).build()
      }
    }
  }
  
  @POST
  @Path("{repoName}")
  def postReceive(@PathParam("repoName")repoName: String, bodyStr: String): Response = {
    def getPostReceiveInfo = {
      val postReceiveInfo = parse(bodyStr).extract[PostReceiveInfo]
      require(postReceiveInfo.repository.name != "")
      postReceiveInfo
    }
    
    try {
      val execGitCmd = execCommand(_: String, 30 * 1000L, 
        new File(getLocalRepoPath(getPostReceiveInfo.repository.name)))
      
      execGitCmd("git pull origin master") match {
        case (0, o, _) => logger.debug(o)
        case (_, _, e) => throw new CommandFailedException(e)
      }
      execGitCmd("git push " + PushRepoName + " master") match {
        case (0, o, _) => logger.debug(o)
        case (_, _, e) => throw new CommandFailedException(e)
      }
      val res = 
        <html xmlns="http://www.w3.org/1999/xhtml">
          <body>Thank you. Repositories ware merged.</body>
        </html>
      Response.ok(res.toString()).build()
    } catch {
      case e: IllegalArgumentException => {
        logger.error(e.getMessage())
        Response.status(400).build()
      }
      case e: CommandFailedException => {
        logger.error(e.getMessage())
        Response.status(500).build()
      }
    }
  }

  @DELETE
  @Path("{repoName}")
  def deleteRepo(@PathParam("repoName")repoName: String): Response = {
    val cmd = "rm -rf " + getLocalRepoPath(repoName);
    execCommand(cmd, 3 * 1000L) match {
      case (0, o, _) => logger.info("Executed: [" + cmd + "]")
      case (_, _, e) => throw new CommandFailedException(e)
    }
    val res = 
      <html xmlns="http://www.w3.org/1999/xhtml">
        <body>Repository [{repoName}] was deleted.</body>
      </html>
    Response.ok().build()
  }
  
}
