package de.htwg.se.Shogi.model.fileIoComponent.fileIoXmlImpl

import com.google.inject.name.Names
import com.google.inject.{ Guice, Injector }
import de.htwg.se.Shogi.model.boardComponent.BoardInterface
import de.htwg.se.Shogi.model.fileIoComponent.DAOInterface
import de.htwg.se.Shogi.model.pieceComponent.PieceInterface
import de.htwg.se.Shogi.model.pieceComponent.pieceBaseImpl.{ PieceFactory, PiecesEnum }
import de.htwg.se.Shogi.model.playerComponent.Player
import de.htwg.se.Shogi.{ ShogiModule, ShogiModuleConf }
import net.codingwell.scalaguice.InjectorExtensions._
import scala.xml.{ Node, NodeSeq, PrettyPrinter }

class FileIO extends DAOInterface {

  override def load: Option[(BoardInterface, Boolean, Player, Player)] = {
    var boardOption: Option[(BoardInterface, Boolean, Player, Player)] = None
    val file = scala.xml.XML.loadFile("board.xml")
    val size = (file \\ "board" \ "@size").text.toInt
    val state = (file \\ "board" \ "@state").text.toString.toBoolean
    val player1 = Player((file \\ "board" \ "@playerFirstName").text.toString, first = true)
    val player2 = Player((file \\ "board" \ "@playerSecondName").text.toString, first = false)
    val injector = Guice.createInjector(new ShogiModule)

    boardOption = getBoardBySize(size, injector) match {
      case Some(board) =>
        val firstPlayer = true
        val secondPlayer = false
        val newBoard = board.setContainer(
          getConqueredPieces(file \\ "board" \ "playerFirstConquered", firstPlayer),
          getConqueredPieces(file \\ "board" \ "playerSecondConquered", secondPlayer)
        )
        Some((newBoard, state, player1, player2))
      case _ => None
    }
    val cellNodes = file \\ "board" \ "cell"
    boardOption match {
      case Some((board, savedState, player_1, player_2)) =>
        var _board = board
        for (cell <- cellNodes) {
          val row: Int = (cell \ "@row").text.toInt
          val col: Int = (cell \ "@col").text.toInt
          val piece = cell \ "piece"
          val pieceName = (piece \ "@pieceName").text.toString
          val firstPlayer = (piece \ "@firstPlayer").text.toBoolean
          PiecesEnum.withNameOpt(pieceName) match {
            case Some(pieceEnum) =>
              _board = _board.replaceCell(col, row, PieceFactory.apply(pieceEnum, firstPlayer))
            case None =>
          }
        }
        boardOption = Some(_board, savedState, player_1, player_2)
      case None =>
    }
    boardOption
  }

  def getBoardBySize(size: Int, injector: Injector): Option[BoardInterface] = {
    size match {
      case ShogiModuleConf.defaultBoardSize =>
        Some(injector.instance[BoardInterface](Names.named(ShogiModuleConf.defaultBoard)).createNewBoard())
      case ShogiModuleConf.smallBoardSize =>
        Some(injector.instance[BoardInterface](Names.named(ShogiModuleConf.smallBoard)).createNewBoard())
      case ShogiModuleConf.tinyBoardSize =>
        Some(injector.instance[BoardInterface](Names.named(ShogiModuleConf.tinyBoard)).createNewBoard())
      case _ => None
    }
  }

  def getConqueredPieces(nodeSeq: NodeSeq, first: Boolean): List[PieceInterface] = {
    var stringList: List[String] = List[String]()
    var pieceList: List[PieceInterface] = List[PieceInterface]()

    for (x <- nodeSeq) yield (x \\ "piece").foreach(i => stringList = stringList :+ (i \\ "@pieceName").text.toString)

    for (
      x: String <- stringList;
      pieceEnum <- PiecesEnum.withNameOpt(x)
    ) yield {
      pieceList = pieceList :+ PieceFactory.apply(pieceEnum, first)
    }
    pieceList
  }

  override def save(board: BoardInterface, state: Boolean, player_1: Player, player_2: Player): Unit = saveString(board, state, player_1, player_2)

  def saveString(board: BoardInterface, state: Boolean, player_1: Player, player_2: Player): Unit = {
    import java.io._
    val pw = new PrintWriter(new File("board.xml"))
    val width: Int = 120
    val step: Int = 4
    val prettyPrinter = new PrettyPrinter(width, step)
    val xml = prettyPrinter.format(boardToXml(board, state, player_1, player_2))
    pw.write(xml)
    pw.close()
  }

  def boardToXml(board: BoardInterface, state: Boolean, player_1: Player, player_2: Player): Node = {
    <board size={ board.size.toString } state={ state.toString } playerFirstName={ player_1.name } playerSecondName={ player_2.name }>
      <playerFirstConquered>
        { for (piece <- board.getContainer._1) yield conqueredToXml(piece) }
      </playerFirstConquered>
      <playerSecondConquered>
        { for (piece <- board.getContainer._2) yield conqueredToXml(piece) }
      </playerSecondConquered>{
        for {
          row <- 0 until board.size
          col <- 0 until board.size
        } yield cellToXml(board, row, col)
      }
    </board>
  }

  def cellToXml(board: BoardInterface, row: Int, col: Int): Node = {
    board.cell(col, row) match {
      case Some(piece) =>
        <cell row={ row.toString } col={ col.toString }>
          <piece pieceName={ piece.name } firstPlayer={ piece.isFirstOwner.toString }/>
        </cell>
      case None =>
        <cell row="Error" col="Error">
          <piece pieceName="Error" firstPlayer="Error"/>
        </cell>
    }
  }

  def conqueredToXml(piece: PieceInterface): Node = {
    <piece pieceName={ piece.name } firstPlayer={ piece.isFirstOwner.toString }/>
  }
}
