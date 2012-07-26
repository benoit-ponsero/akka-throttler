package akka.util.throttle

import java.util.concurrent.TimeUnit
import akka.util.Duration
import akka.actor.ActorRef
import akka.actor.Actor
import akka.AkkaException

/**
 * A rate used for throttling.
 *
 * There are some shorthands available to construct rates:
 * {{{
 *  import java.util.concurrent.TimeUnit._
 *  import akka.util.throttle.Rate
 *  import akka.util.throttle.Throttling._
 *  import akka.util.Duration
 *
 *  val rate1 = 1 msgsPer (1, SECONDS)
 *  val rate2 = 1 msgsPer Duration(1, SECONDS)
 *  val rate3 = 1 msgsPerSecond
 *  val rate4 = 1 msgsPerMinute
 *  val rate5 = 1 msgsPerHour
 * }}}
 *
 * @param numberOfCalls the number of calls that may take place in a period
 * @param duration the length of the period
 * @param timeUnit the time-unit used to express the duration
 * @see [[akka.util.throttle.Throttler]]
 */
case class Rate(val numberOfCalls: Int, val duration: Duration) {
  /**
   * The duration in milliseconds.
   */
  def durationInMillis(): Long = duration.toMillis
}

/**
 * Marker trait for throttlers.
 *
 * A [[akka.util.throttle.Throttler]] understands actor messages of type
 * [[akka.util.throttle.SetTarget]], [[akka.util.throttle.SetRate]], and
 * [[akka.util.throttle.Queue]].
 *
 * A <em>throttler</em> is an actor that is defined through a <em>target actor</em> and a <em>rate</em>
 * (of type [[akka.util.throttle.Rate]]). You set or change the target and rate at any time through the `SetTarget(target)`
 * and `SetRate(rate)` messages, respectively. When you send the throttler a message `Queue(msg)`, it will
 * put the message `msg` into an internal queue and eventually send all queued messages to the target, at
 * a speed that respects the given rate. If no target is currently defined then the messages will be queued
 * and will be delivered as soon as a target gets set.
 *
 * Notice that the throttler `forward`s messages, i.e., the target will see the original sender of the message
 * (and not the throttler) as the message's sender.
 *
 * It is left to the implementation whether the internal queue is persited over application restarts or
 * actor failure.
 *
 * @see [[akka.util.throttle.TimerBasedThrottler]]
 */
trait Throttler { self: Actor => }

/**
 * Exception thrown by a [[akka.util.throttle.Throttler]] in case message delivery to the target
 * fails for a message `msg` queued to the throttler via `Queue(msg)`. In this way, the throttler's
 * supervisor can take appropriate action.
 */
class FailedToSendException(message: String, cause: Throwable) extends AkkaException(message, cause)

/**
 * Set the target of a [[akka.util.throttle.Throttler]].
 *
 * You may change a throttler's target at any time.
 *
 * Notice that the messages sent by the throttler to the target will have the original sender (and
 * not the throttler) as the sender. (In Akka terms, the throttler `forward`s the message.)
 *
 * @param target if `target` is `None`, the throttler will stop delivering messages and the messages already received
 *  but not yet delivered, as well as any messages received in future `Queue(msg)` messages will be queued
 *  and eventually be delivered when a new target is set. If `target` is defined, the currently queued messages
 *  as well as any messages received in the the future through `Queue(msg)` messages will be delivered
 *  to the new target at a rate not exceeding the current throttler's rate.
 */
case class SetTarget(target: Option[ActorRef])

/**
 * Set the rate of a [[akka.util.throttle.Throttler]].
 *
 * You may change a throttler's rate at any time.
 *
 * @param rate the rate at which messages will be delivered to the target of the throttler
 */
case class SetRate(rate: Rate)

/**
 * Queue a message to a [[akka.util.throttle.Throttler]].
 *
 * The message `msg` will eventually be sent to the throttler's target. This may happen immediately
 * or after a delay that may be necessary in order to not deliver messages at a rate higher than the
 * throttler's current rate.
 *
 * Should the throttler not be able to send the message, it will throw a
 * [[akka.util.throttle.FailedToSendException]] so that the throttler's supervisor
 * can take appropriate action.
 *
 * @param msg the message to eventually be sent to the throttler's target; the target will see the original
 *    sender (and not the throttler) as the messages sender
 */
case class Queue(msg: Any)

/**
 * Implicits for a few nice DSL-like constructs.
 *
 * @see [[akka.util.throttle.Rate]]
 */
object Throttling {

  /**
   * @see [[akka.util.throttle.Rate]]
   */
  class RateInt(numberOfCalls: Int) {
    def msgsPer(duration: Int, timeUnit: TimeUnit) = Rate(numberOfCalls, Duration(duration, timeUnit))
    def msgsPer(duration: Duration) = Rate(numberOfCalls, duration)
    def msgsPerSecond = Rate(numberOfCalls, Duration(1, TimeUnit.SECONDS))
    def msgsPerMinute = Rate(numberOfCalls, Duration(1, TimeUnit.MINUTES))
    def msgsPerHour = Rate(numberOfCalls, Duration(1, TimeUnit.HOURS))
  }

  /**
   * @see [[akka.util.throttle.Rate]]
   */
  implicit def toRateInt(numberOfCalls: Int) = new RateInt(numberOfCalls)
}
