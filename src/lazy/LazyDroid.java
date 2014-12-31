package lazy;

import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;

import java.awt.geom.Point2D;
import java.io.Serializable;

import lazy.LazyDroid.LazyBeans.AbsolutePoint;
import lazy.LazyDroid.LazyBeans.OrbitPointMessage;
import robocode.Droid;
import robocode.MessageEvent;

/**
 * Base on the LunarTwins team (Alexander Schultz)
 * Author : viet.nguyen
 *          viet.nguyen@exoplatform.com
 *          tuan.pham
 *          tuan.pham@exoplatform.com
 */
public class LazyDroid extends LazyHero implements Droid {

  private static OrbitPointMessage lastmessage;

  public LazyDroid() {
    lastmessage = null;
  }

  public void onMessageReceived(MessageEvent e) {
    try {
      lastmessage = (OrbitPointMessage) e.getMessage();
    } catch (Exception ex) {
    }
  }

  public AbsolutePoint getGoal() {
    try {
      // Tell the hero where we are
      broadcastMessage(location);
      return lastmessage.goal;
    } catch (Exception ex) {
      return null;
    }
  }

  public AbsolutePoint getTarget() {
    if (lastmessage == null)
      return null;
    // Fire at the location predicted by CircularTargeting
    return lastmessage.robot.predict(location, getFirePower());
  }

  public double currentDist() {
    return location.distance(lastmessage.robot.location);
  }

  public void doRadar() {
  }

  public void setFire(double power) {
  }

  public static class LazyBeans {
    public static class OrbitPointMessage implements Serializable {
      private static final long serialVersionUID = 1L;

      public AbsolutePoint      goal;

      public EnemyRobot         robot;

      public OrbitPointMessage(AbsolutePoint g, EnemyRobot r) {
        goal = g;
        robot = r;
      }
    }

    public static class AbsolutePoint extends Point2D implements Serializable {
      private static final long serialVersionUID = 1L;

      public double             x, y;

      private AbsolutePoint() {
      }

      public double getX() {
        return x;
      }

      public double getY() {
        return y;
      }

      public AbsolutePoint addRelativePoint(RelativePoint rel) {
        return fromXY(x + rel.getX(), y + rel.getY());
      }

      public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
      }

      public static AbsolutePoint fromXY(double x, double y) {
        AbsolutePoint newpoint = new AbsolutePoint();
        newpoint.setLocation(x, y);
        return newpoint;
      }
    }

    public final static class RelativePoint extends Point2D implements Serializable {
      private static final long serialVersionUID = 1L;

      public double             x, y, direction, magnitude;

      public double getX() {
        return x;
      }

      public double getY() {
        return y;
      }

      public double getDirection() {
        return direction;
      }

      public double getDirDist(double dir) {
        return normalRelativeAngle(direction - dir);
      }

      public double getMagnitude() {
        return magnitude;
      }

      public RelativePoint addRelativePoint(RelativePoint rel) {
        return fromXY(getX() + rel.getX(), getY() + rel.getY());
      }

      public RelativePoint incrementDirMag(double direction, double magnitude) {
        return fromDM(this.direction + direction, this.magnitude + magnitude);
      }

      public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
        direction = normalAbsoluteAngle(Math.atan2(x, y));
        magnitude = Math.sqrt(x * x + y * y);
      }

      public void setDirectionMagnitude(double direction, double magnitude) {
        this.direction = normalAbsoluteAngle(direction);
        this.magnitude = magnitude;
        x = magnitude * Math.sin(this.direction);
        y = magnitude * Math.cos(this.direction);
      }

      private static RelativePoint getNewInstance() {
        return new RelativePoint();
      }

      public static RelativePoint fromXY(double x, double y) {
        RelativePoint newpoint = getNewInstance();
        newpoint.setLocation(x, y);
        return newpoint;
      }

      public static RelativePoint fromDM(double direction, double magnitude) {
        RelativePoint newpoint = getNewInstance();
        newpoint.setDirectionMagnitude(direction, magnitude);
        return newpoint;
      }

      public static RelativePoint fromPP(AbsolutePoint p1, AbsolutePoint p2) {
        RelativePoint newpoint = getNewInstance();
        newpoint.setLocation(p1.getX() - p2.getX(), p1.getY() - p2.getY());
        return newpoint;
      }
    }
  }

}
