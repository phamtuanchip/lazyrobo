package lazy;

import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;

import java.io.Serializable;

import lazy.LazyDroid.LazyBeans.AbsolutePoint;
import lazy.LazyDroid.LazyBeans.OrbitPointMessage;
import lazy.LazyDroid.LazyBeans.RelativePoint;
import robocode.MessageEvent;
import robocode.ScannedRobotEvent;
import robocode.TeamRobot;

/**
 * Base on the LunarTwins team (Alexander Schultz)
 * Author : viet.nguyen
 *          viet.nguyen@exoplatform.com
 *          tuan.pham
 *          tuan.pham@exoplatform.com
 */
public class LazyHero extends TeamRobot {

  public static AbsolutePoint location, allylocation;

  public static final int     bfheight   = 600, bfwidth = 800;

  private static EnemyRobot   enemyleader, otherenemy;

  private static final double turnpast   = (2 * Math.PI) / 72;

  private static final int    ROBOT_SIZE = 18;

  private int                 bossAmount = 0;

  public LazyHero() {
    enemyleader = otherenemy = null;
    allylocation = null;
  }

  public void run() {
    setAdjustRadarForGunTurn(true);
    setAdjustGunForRobotTurn(true);
    setAdjustRadarForRobotTurn(true);
    while (true) {
      location = AbsolutePoint.fromXY(getX(), getY());
      AbsolutePoint target = getTarget();
      if (target != null) {
        RelativePoint rel = RelativePoint.fromPP(target, location);
        setTurnGunRightRadians(rel.getDirDist(getGunHeadingRadians()));
        setFire(getFirePower());
      }
      AbsolutePoint goal = getGoal();
      if (goal != null) {
        RelativePoint rel = RelativePoint.fromPP(goal, location);
        double turn = rel.getDirDist(getHeadingRadians());
        if (Math.abs(turn) > Math.PI / 2) {
          turn = normalRelativeAngle(turn + Math.PI);
          setBack(rel.getMagnitude());
        } else {
          setAhead(rel.getMagnitude());
        }
        setTurnRightRadians(turn);
      }
      doRadar();
      execute();
    }
  }

  public void onScannedRobot(ScannedRobotEvent e) {
    if (e.getEnergy() <= 300) {
      if (!isTeammate(e.getName())) {
        if (e.getEnergy() >= 150 || e.getEnergy() < 100 || (enemyleader != null && enemyleader.name == e.getName())) {
          enemyleader = EnemyRobot.gotScan(enemyleader, this, e);
        } else {
          otherenemy = EnemyRobot.gotScan(otherenemy, this, e);
        }
      }
    } else {
      bossAmount = 1;
    }
  }

  public void onMessageReceived(MessageEvent e) {
    try {
      allylocation = (AbsolutePoint) e.getMessage();
    } catch (Exception ex) {
    }
  }

  public AbsolutePoint getTarget() {
    try {
      // Fire at the location predicted by CircularTargeting
      EnemyRobot e = (EnemyRobot) getClosest().clone();
      return e.predict(location, getFirePower());
    } catch (Exception ex) {
      return null;
    }
  }

  public AbsolutePoint getGoal() {
    // If we know where our ally is, calculate our average location
    AbsolutePoint avglocation = location;
    if (allylocation != null) {
      RelativePoint rel2 = RelativePoint.fromPP(allylocation, location);
      rel2.setDirectionMagnitude(rel2.getDirection(), rel2.getMagnitude() / 2);
      avglocation = location.addRelativePoint(rel2);
    }
    try {
      // Calculate where the average location is relative to the enemy
      EnemyRobot predictplace = (EnemyRobot) getClosest().clone();
      predictplace.predictTick();
      RelativePoint rel = RelativePoint.fromPP(predictplace.location, avglocation);
      // Create goal points on each side of the enemy
      rel = RelativePoint.fromDM(rel.getDirection() + (Math.PI / 2), 50);
      AbsolutePoint goal = predictplace.location.addRelativePoint(rel);
      AbsolutePoint goal2 = predictplace.location.addRelativePoint(rel.incrementDirMag(Math.PI, 0));
      // If we know where our ally is, untangle our paths
      if (allylocation != null) {
        double a1 = goal.distance(location), b1 = goal2.distance(allylocation);
        double a2 = goal2.distance(location), b2 = goal.distance(allylocation);
        if (!(a1 < a2 && a1 < b2) && !(b1 < a2 && b1 < b2)) {
          AbsolutePoint tmp = goal;
          goal = goal2;
          goal2 = tmp;
        }
        // Adjust the goal locations to not run into the enemy
        RelativePoint g1 = RelativePoint.fromPP(predictplace.location, goal2);
        RelativePoint g2 = RelativePoint.fromPP(predictplace.location, goal);
        RelativePoint o1 = RelativePoint.fromPP(predictplace.location, location);
        RelativePoint o2 = RelativePoint.fromPP(predictplace.location, allylocation);
        g1.direction = fixDirection(g1, o1);
        g2.direction = fixDirection(g2, o2);
        goal = predictplace.location.addRelativePoint(g1);
        goal2 = predictplace.location.addRelativePoint(g2);
      }
      goal = wallAdjust(goal, predictplace.location);
      goal2 = wallAdjust(goal2, predictplace.location);
      // Tell our ally where the enemy is, where to move relative to it, and
      // scan data of the enemy.
      broadcastMessage(new OrbitPointMessage(goal2, (EnemyRobot) predictplace.clone()));
      return goal;
    } catch (Exception e) {
      return null;
    }
  }

  public void doRadar() {
    double radarturn;
    try {
      // Calculate the angle the radar needs to turn to point at the enemy
      radarturn = getClosest().relativelocation.getDirDist(getRadarHeadingRadians());
      // And turn past it some
      radarturn += Math.signum(radarturn) * turnpast;
    } catch (Exception ex) {
      radarturn = Double.POSITIVE_INFINITY;
    }
    setTurnRadarRightRadians(radarturn);
  }

  public EnemyRobot getClosest() {
    if ((getTeammates() != null) && (getOthers() - bossAmount - getTeammates().length >= 2) || isUpToDate(enemyleader))
      return enemyleader;
    else if (isUpToDate(otherenemy))
      return otherenemy;
    else
      return null;
  }

  public double getFirePower() {
    double distance = currentDist();
    if (distance < 75)
      return 3.0;
    else if (distance < 600)
      return 2.5;
    return 2.0;
  }

  public double currentDist() {
    return getClosest().relativelocation.getMagnitude();
  }

  public boolean isUpToDate(EnemyRobot enemy) {
    return (enemy != null && getTime() - enemy.time <= 1);
  }

  public static AbsolutePoint wallAdjust(AbsolutePoint goal, AbsolutePoint target) {
    double x, y;
    if (target.getY() > goal.getY()) {
      y = target.getY() - 50;
    } else {
      y = target.getY() + 50;
    }
    if (target.getX() > goal.getX()) {
      x = target.getX() - 50;
    } else {
      x = target.getX() + 50;
    }
    if (Math.round(goal.getX()) > bfwidth - ROBOT_SIZE) {
      x = bfwidth - (ROBOT_SIZE + 1);
    } else if (Math.round(goal.getX()) < ROBOT_SIZE) {
      x = ROBOT_SIZE + 1;
    } else if (Math.round(goal.getY()) > bfheight - ROBOT_SIZE) {
      y = bfheight - (ROBOT_SIZE + 1);
    } else if (Math.round(goal.getY()) < ROBOT_SIZE) {
      y = ROBOT_SIZE + 1;
    } else {
      return goal;
    }
    return AbsolutePoint.fromXY(x, y);
  }

  public static double fixDirection(RelativePoint g, RelativePoint o) {
    double rel = normalRelativeAngle(o.direction - g.direction);
    if (rel > Math.PI / 2)
      rel = Math.PI / 2;
    if (rel < -Math.PI / 2)
      rel = -Math.PI / 2;
    return normalAbsoluteAngle(o.direction + rel);
  }

  public static class EnemyRobot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String             name;

    public RelativePoint      relativelocation, velocity;

    public AbsolutePoint      location;

    public double             angularvelocity;

    public long               time;

    public EnemyRobot() {
    }

    private EnemyRobot(EnemyRobot old) {
      name = old.name;
      relativelocation = old.relativelocation;
      location = old.location;
      velocity = old.velocity;
      angularvelocity = old.angularvelocity;
      time = old.time;
    }

    public void onScannedRobotEvent(LazyHero bot, ScannedRobotEvent e) {
      name = e.getName();
      relativelocation = RelativePoint.fromDM(e.getBearingRadians() + bot.getHeadingRadians(), e.getDistance());
      location = AbsolutePoint.fromXY(bot.getX(), bot.getY()).addRelativePoint(relativelocation);
      // Rolling average angular velocity
      if (velocity != null) {
        angularvelocity = (angularvelocity * 3 + velocity.getDirDist(e.getHeadingRadians())) / 4;
      }
      velocity = RelativePoint.fromDM(e.getHeadingRadians(), e.getVelocity());
      time = e.getTime();
    }

    public void predictTick() {
      velocity = velocity.incrementDirMag(-angularvelocity, 0);
      location = location.addRelativePoint(velocity);
      relativelocation = relativelocation.addRelativePoint(velocity);
      if (processWallCollision(location, velocity.getDirection()))
        velocity.setDirectionMagnitude(velocity.getDirection(), 0);
      time++;
    }

    public AbsolutePoint predict(AbsolutePoint from, double bulletpower) {
      long start = time;
      while ((location.distance(from) - ROBOT_SIZE) > ((time - start) * (20 - 3 * bulletpower)))
        predictTick();
      return location;
    }

    public Object clone() {
      return new EnemyRobot(this);
    }

    public static boolean processWallCollision(AbsolutePoint goal, AbsolutePoint start) {
      return processWallCollision(goal, RelativePoint.fromPP(goal, start).getDirection());
    }

    public static boolean processWallCollision(AbsolutePoint location, double direction) {
      boolean hitWall = false;
      double fixx = 0, fixy = 0;
      if (Math.round(location.getX()) > LazyHero.bfwidth - ROBOT_SIZE) {
        hitWall = true;
        fixx = LazyHero.bfwidth - ROBOT_SIZE - location.getX();
      }
      if (Math.round(location.getX()) < ROBOT_SIZE) {
        hitWall = true;
        fixx = ROBOT_SIZE - location.getX();
      }
      if (Math.round(location.getY()) > LazyHero.bfheight - ROBOT_SIZE) {
        hitWall = true;
        fixy = LazyHero.bfheight - ROBOT_SIZE - location.getY();
      }
      if (Math.round(location.getY()) < ROBOT_SIZE) {
        hitWall = true;
        fixy = ROBOT_SIZE - location.getY();
      }
      double tanHeading = Math.tan(direction);
      double fixxtanHeading = fixx / tanHeading, fixytanHeading = fixy * tanHeading;
      // if it hits bottom or top wall
      if (fixx == 0) {
        fixx = fixytanHeading;
      } // if it hits a side wall
      else if (fixy == 0) {
        fixy = fixxtanHeading;
      } // if the robot hits 2 walls at the same time (rare, but just in case)
      else if (Math.abs(fixxtanHeading) > Math.abs(fixy)) {
        fixy = fixxtanHeading;
      } else if (Math.abs(fixytanHeading) > Math.abs(fixx)) {
        fixx = fixytanHeading;
      }
      location.setLocation(location.x + fixx, location.y + fixy);
      return hitWall;
    }

    public static EnemyRobot gotScan(EnemyRobot old, LazyHero bot, ScannedRobotEvent e) {
      if (old == null)
        old = new EnemyRobot();
      old.onScannedRobotEvent(bot, e);
      return old;
    }
  }
}
