package org.apache.iotdb.db.cluster;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.service.IoTDB;

/**
 * @author lta
 */
public class IoTDBNode extends AbstractActor {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  private Cluster cluster = Cluster.get(getContext().system());
  private IoTDB daemon;

  static public Props props() {
    return Props.create(IoTDBNode.class, () -> new IoTDBNode());
  }

  @Override
  public void preStart() {
    cluster.subscribe(self(), ClusterEvent.initialStateAsEvents(),
        MemberEvent.class, UnreachableMember.class);
    daemon = IoTDB.getInstance();
    daemon.active();
  }

  @Override
  public void postStop() throws FileNodeManagerException {
    cluster.unsubscribe(self());
    daemon.stop();
  }

  public IoTDBNode() {
  }


  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(MemberUp.class, mUp -> {
          log.info("IoTDB node is Up: {}", mUp.member());
        })
        .match(UnreachableMember.class, mUnreachable -> {
          log.info("IoTDB node detected as unreachable: {}", mUnreachable.member());
        })
        .match(MemberRemoved.class, mRemoved -> {
          log.info("IoTDB node is Removed: {}", mRemoved.member());
        })
        .match(MemberEvent.class, message -> {
          // ignore
        })
        .build();
  }
}
