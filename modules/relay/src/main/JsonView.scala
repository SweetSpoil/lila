package lila.relay

import play.api.libs.json._
import scala.concurrent.duration._

import lila.common.config.BaseUrl
import lila.common.Json.jodaWrites

final class JsonView(baseUrl: BaseUrl, markup: RelayMarkup) {

  import JsonView._

  def apply(trs: RelayTour.WithRounds, currentRoundId: RelayRound.Id, admin: Boolean) = {
    val adminRound = admin ?? trs.rounds.find(_.id == currentRoundId)
    Json
      .obj(
        "tour" -> Json
          .obj(
            "id"          -> trs.tour.id,
            "name"        -> trs.tour.name,
            "slug"        -> trs.tour.slug,
            "description" -> trs.tour.description
          )
          .add("credit", trs.tour.credit)
          .add("markup" -> trs.tour.markup.map(markup.apply)),
        "rounds" -> trs.rounds
      )
      .add("sync" -> adminRound.map(_.sync))
  }

  def sync(round: RelayRound) = syncWrites writes round.sync

  def makeData(
      trs: RelayTour.WithRounds,
      currentRoundId: RelayRound.Id,
      studyData: lila.study.JsonView.JsData,
      canContribute: Boolean
  ) =
    JsData(
      relay = apply(trs, currentRoundId, canContribute),
      study = studyData.study,
      analysis = studyData.analysis
    )
}

object JsonView {

  case class JsData(relay: JsObject, study: JsObject, analysis: JsObject)

  implicit val syncLogEventWrites = Json.writes[SyncLog.Event]

  implicit val roundIdWrites: Writes[RelayRound.Id] = Writes[RelayRound.Id] { id =>
    JsString(id.value)
  }

  implicit val roundWrites: OWrites[RelayRound] = OWrites[RelayRound] { round =>
    Json
      .obj(
        "id"   -> round.id,
        "name" -> round.name,
        "slug" -> round.slug
      )
      .add("finished" -> round.finished)
      .add("ongoing" -> (round.hasStarted && !round.finished))
      .add("startsAt" -> round.startedAt.orElse(round.startsAt))
  }

  implicit val tourIdWrites: Writes[RelayTour.Id] = Writes[RelayTour.Id] { id =>
    JsString(id.value)
  }

  implicit private val syncWrites: OWrites[RelayRound.Sync] = OWrites[RelayRound.Sync] { s =>
    Json.obj(
      "ongoing" -> s.ongoing,
      "log"     -> s.log.events
    ) ++
      s.upstream.?? {
        case RelayRound.Sync.UpstreamUrl(url) => Json.obj("url" -> url)
        case RelayRound.Sync.UpstreamIds(ids) => Json.obj("ids" -> ids)
      }
  }
}
