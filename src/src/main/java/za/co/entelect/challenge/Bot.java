package za.co.entelect.challenge;

import za.co.entelect.challenge.command.*;
import za.co.entelect.challenge.entities.*;
import za.co.entelect.challenge.enums.Terrain;
import za.co.entelect.challenge.enums.PowerUps;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.abs;

public class Bot {

    private static final int maxSpeed = 9;

    private Random random;
    private GameState gameState;
    private Car opponent;
    private Car myCar;

    private final static Command ACCELERATE = new AccelerateCommand();
    private final static Command LIZARD = new LizardCommand();
    private final static Command OIL = new OilCommand();
    private final static Command BOOST = new BoostCommand();
    private final static Command EMP = new EmpCommand();
    private final static Command FIX = new FixCommand();
    private final static Command NOTHING = new DoNothingCommand();

    private final static Command TURN_RIGHT = new ChangeLaneCommand(1);
    private final static Command TURN_LEFT = new ChangeLaneCommand(-1);

    public Bot(Random random, GameState gameState) {
        this.random = random;
        this.gameState = gameState;
        this.myCar = gameState.player;
        this.opponent = gameState.opponent;
    }

    public Command run() {
        // Take all the terrain blocks in front of the car on the same lane
        List<Object> blocks = getBlocksInFront(myCar.position.lane, myCar.position.block, gameState);
        List<Object> blocksCurrentSpeed = getBlocksInFrontCurrentSpeed(myCar.position.lane, myCar.position.block,
                gameState, myCar);
        List<Object> blocksAccelerateSpeed = getBlocksInFrontAccelerateSpeed(myCar.position.lane, myCar.position.block,
                gameState, myCar);
        List<Object> blocksBoost = getBlocksInFrontBoost(myCar.position.lane, myCar.position.block, gameState);

        // Take all the terrain blocks in front of the car with in current speed
        List<Object> blocks1CurrentSpeed = getBlocksInFrontCurrentSpeed(1, myCar.position.block, gameState, myCar);
        List<Object> blocks2CurrentSpeed = getBlocksInFrontCurrentSpeed(2, myCar.position.block, gameState, myCar);
        List<Object> blocks3CurrentSpeed = getBlocksInFrontCurrentSpeed(3, myCar.position.block, gameState, myCar);
        List<Object> blocks4CurrentSpeed = getBlocksInFrontCurrentSpeed(4, myCar.position.block, gameState, myCar);

        // Take all the terrain blocks in front of the car in boost speed
        List<Object> blocks1Boost = getBlocksInFrontBoost(1, myCar.position.block, gameState);
        List<Object> blocks2Boost = getBlocksInFrontBoost(2, myCar.position.block, gameState);
        List<Object> blocks3Boost = getBlocksInFrontBoost(3, myCar.position.block, gameState);
        List<Object> blocks4Boost = getBlocksInFrontBoost(4, myCar.position.block, gameState);

        // Fix first if too much damage
        if (myCar.damage >= 2) {
            return FIX;
        }

        // Accelerate if speed below 4 and theres no obstacles ahead
        if ((myCar.speed <= 3) && (!isObstacle(myCar, blocksAccelerateSpeed, 1))) {
            return ACCELERATE;
        }

        // Dodge logic
        if (isObstacle(myCar, blocksCurrentSpeed, 1) || isCollided(myCar, opponent)
                || (isObstacle(myCar, blocksBoost, 1) && (myCar.speed == 15))) {
            // If boosting, check more blocks ahead
            if (myCar.speed == 15) {
                if (isLizardNice(myCar, blocksBoost)) {
                    return LIZARD;
                } else {
                    return turnObstacle(myCar, blocksCurrentSpeed, blocks1Boost, blocks2Boost, blocks3Boost, blocks4Boost,
                            blocksAccelerateSpeed);
                }
                // not boosting
            } else if (myCar.speed < 15) {
                if (isLizardNice(myCar, blocksCurrentSpeed)) {
                    return LIZARD;
                } else {
                    return turnObstacle(myCar, blocksCurrentSpeed, blocks1CurrentSpeed, blocks2CurrentSpeed, blocks3CurrentSpeed,
                            blocks4CurrentSpeed, blocksAccelerateSpeed);
                }
            }
        }

        // Improvement logic
        if (myCar.speed < 8) {
            if (isBoostNice(myCar, blocksBoost)) {
                return BOOST;
            } else if (!isObstacle(myCar, blocksAccelerateSpeed, 1)) {
                return ACCELERATE;
            }
        }

        // Attack logic
        // Player's car is in front of the opponent
        if (inFront(myCar, opponent)) {
            if (isBoostNice(myCar, blocksBoost)) {
                return BOOST;
            } else if (hasPowerUp(PowerUps.OIL, myCar.powerups) && (myCar.position.block - opponent.position.block <= 2)
                    && (isBeside(myCar, opponent))) {
                return OIL;
            } else if (hasPowerUp(PowerUps.TWEET, myCar.powerups)) {
                if (opponent.speed <= 3) {
                    // Opponent is more likely to prioritize accelerating
                    return new TweetCommand(opponent.position.lane, opponent.position.block + opponent.speed + 4);
                } else {
                    return new TweetCommand(opponent.position.lane, opponent.position.block + opponent.speed + 1);
                }
            }
        }
        // Opponent is in front of player's car
        if (inFront(opponent, myCar)) {
            if (isEMPNice(myCar, opponent, blocks)) {
                return EMP;
            } else if (isBoostNice(myCar, blocksBoost)) {
                return BOOST;
            } else if (hasPowerUp(PowerUps.TWEET, myCar.powerups)) {
                if (opponent.speed <= 3) {
                    // Opponent is more likely to prioritize accelerating
                    return new TweetCommand(opponent.position.lane, opponent.position.block + opponent.speed + 4);
                } else {
                    return new TweetCommand(opponent.position.lane, opponent.position.block + opponent.speed + 1);
                }
            }
        }

        // Waste oil to gain more points
        if (myCar.speed >= 9 && hasPowerUp(PowerUps.OIL, myCar.powerups)) {
            return OIL;
        }

        // Fix to perfect condition, preparation to use boost if player's position is
        // behind the opponent
        if ((hasPowerUp(PowerUps.BOOST, myCar.powerups)) && (myCar.damage >= 1) && (myCar.speed == 9)
                && inFront(opponent, myCar)) {
            return FIX;
        }

        // Default state if nothing happened
        if (isObstacle(myCar, blocksAccelerateSpeed, 1)) {
            return turnObstacle(myCar, blocksCurrentSpeed, blocks1CurrentSpeed, blocks2CurrentSpeed, blocks3CurrentSpeed,
                    blocks4CurrentSpeed, blocksAccelerateSpeed);
        }

        return ACCELERATE;
    }

    /**
     * Returns true if it's better to use EMP
     * EMP is nice to use when player's position is far behind, player's lane is in
     * range,
     * and opponent is moving at high speed
     **/
    private Boolean isEMPNice(Car myCar, Car opponent, List<Object> blocks) {
        return (opponent.speed >= 5 && isBeside(myCar, opponent) && hasPowerUp(PowerUps.EMP, myCar.powerups)
                && (opponent.position.block - myCar.position.block > myCar.speed));
    }

    /**
     * Returns true if it's better to use lizard
     **/
    private Boolean isLizardNice(Car myCar, List<Object> blocks) {
        // If near finish line and want to use lizard, just use it without scanning
        // obstacles ahead
        if ((1500 - myCar.position.block <= myCar.speed) && (hasPowerUp(PowerUps.LIZARD, myCar.powerups))) {
            return true;
        } else if (hasPowerUp(PowerUps.LIZARD, myCar.powerups) && (scoreLane(myCar, blocks, 1) < -2)
                && (!isObstacle(myCar, blocks.subList(myCar.speed, myCar.speed + 1), 0))) {
            // Scan if there's no obstacle on the landing block
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns true if it's better to use boost
     * Boost is nice to use when car damage is 0 and there's no obstacle in front
     * Also do not use boost in boosting state so not to waste the powerups
     **/
    private Boolean isBoostNice(Car myCar, List<Object> blocksBoost) {
        return (hasPowerUp(PowerUps.BOOST, myCar.powerups) && !isObstacle(myCar, blocksBoost, 1) && myCar.damage == 0
                && !myCar.boosting);
    }

    /**
     * Returns true if player's car is beside opoonent's car lane
     **/
    private Boolean isBeside(Car myCar, Car opponent) {
        return (abs(myCar.position.lane - opponent.position.lane) <= 1);
    }

    /**
     * Returns true if player's car is collided with opponent
     **/
    private Boolean isCollided(Car myCar, Car opponent) {
        return (myCar.position.lane == opponent.position.lane && myCar.position.block - opponent.position.block == -1);
    }

    /**
     * Returns a lane in which the car most likely profits more
     * mode 1 = start from where the car is
     * mode 2 = start from the front of the car
     **/
    private int scoreLane(Car myCar, List<Object> blocks, int mode) {
        int score = 0;
        for (int i = mode; i <= blocks.size(); i++) {
            if (blocks.subList(i - 1, i).contains(Terrain.MUD)) {
                score -= 2;
            } else if (blocks.subList(i - 1, i).contains(Terrain.OIL_SPILL)) {
                score -= 1;
            } else if (blocks.subList(i - 1, i).contains(Terrain.WALL)) {
                score -= 5;
            } else if (blocks.subList(i - 1, i).contains(Terrain.BOOST)) {
                score += 2;
            } else if (blocks.subList(i - 1, i).contains(Terrain.TWEET)) {
                score += 1;
            } else if (blocks.subList(i - 1, i).contains(Terrain.EMP)) {
                if (inFront(myCar, opponent)) {
                    score += 0;
                } else if (inFront(opponent, myCar)) {
                    score += 2;
                }
            }
        }
        return score;
    }

    /**
     * Returns true if there are obstacles ahead
     * mode 0 = start from where the car is
     * mode 1 = start from the front of the car
     **/
    private Boolean isObstacle(Car myCar, List<Object> blocks, int mode) {
        return (blocks.subList(mode, blocks.size()).contains(Terrain.MUD)
                || blocks.subList(mode, blocks.size()).contains(Terrain.WALL)
                || blocks.subList(mode, blocks.size()).contains(Terrain.OIL_SPILL));
    }

    // private Boolean isObstacle(Car myCar, List<Object> blocks) {
    //     return (blocks.contains(Terrain.MUD) || blocks.contains(Terrain.WALL) || blocks.contains(Terrain.OIL_SPILL));
    // }

    /**
     * Returns true if the first parameter car is in front of
     * the second parameter car and vice versa
     **/
    private Boolean inFront(Car myCar, Car opponent) {
        return (myCar.position.block > opponent.position.block);
    }

    /**
     * Returns true if car has a particular power up
     * and false if car doesn't have power up
     **/
    private Boolean hasPowerUp(PowerUps powerUpToCheck, PowerUps[] available) {
        for (PowerUps powerUp : available) {
            if (powerUp.equals(powerUpToCheck)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns command buat belok
     **/
    private Command turnObstacle(Car myCar, List<Object> blocks, List<Object> blocks1, List<Object> blocks2,
            List<Object> blocks3, List<Object> blocks4, List<Object> blocksAccelerate) {
        int scoreAccelerate = scoreLane(myCar, blocksAccelerate, 2);
        int scoreFront = scoreLane(myCar, blocks, 2);
        int score1 = scoreLane(myCar, blocks1, 1);
        int score2 = scoreLane(myCar, blocks2, 1);
        int score3 = scoreLane(myCar, blocks3, 1);
        int score4 = scoreLane(myCar, blocks4, 1);


        if (myCar.position.lane == 1) {
            if (isObstacle(myCar, blocks2, 0)) {
                if (scoreFront < score2) {
                    return TURN_RIGHT;
                } else {
                    if ((scoreAccelerate >= scoreFront) && (!blocksAccelerate.contains(Terrain.WALL)))
                        return ACCELERATE;
                    if ((myCar.speed >= 9) && (hasPowerUp(PowerUps.OIL, myCar.powerups)))
                        return OIL;
                    return NOTHING;
                }
            }
            return TURN_RIGHT;
        } else if (myCar.position.lane == 4) {
            if (isObstacle(myCar, blocks3, 0)) {
                if (scoreFront < score3) {
                    return TURN_LEFT;
                } else {
                    if ((scoreAccelerate >= scoreFront) && (!blocksAccelerate.contains(Terrain.WALL)))
                        return ACCELERATE;
                    if ((myCar.speed >= 9) && (hasPowerUp(PowerUps.OIL, myCar.powerups)))
                        return OIL;
                    return NOTHING;
                }
            }
            return TURN_LEFT;
        } else if (myCar.position.lane == 2) {
            if (isObstacle(myCar, blocks1, 0) && isObstacle(myCar, blocks3, 0)) {
                if ((score1 > score3) && (score1 > scoreFront)) {
                    return TURN_LEFT;
                } else if ((score3 > score1) && (score3 > scoreFront)) {
                    return TURN_RIGHT;
                } else if ((scoreFront > score1) && (scoreFront > score3)) {
                    if ((scoreAccelerate >= scoreFront) && (!blocksAccelerate.contains(Terrain.WALL))) {
                        return ACCELERATE;
                    }
                    if ((myCar.speed >= 9) && (hasPowerUp(PowerUps.OIL, myCar.powerups))) {
                        return OIL;
                    }
                    return NOTHING;
                }
            } else if (!isObstacle(myCar, blocks1, 0) && !isObstacle(myCar, blocks3, 0)) {
                if ((score1 >= score3) && (score1 > scoreFront)) {
                    return TURN_LEFT;
                } else if ((score3 > score1) && (score3 > scoreFront)) {
                    return TURN_RIGHT;
                }
            } else if (isObstacle(myCar, blocks1, 0) && !isObstacle(myCar, blocks3, 0)) {
                return TURN_RIGHT;
            } else if (isObstacle(myCar, blocks3, 0) && !isObstacle(myCar, blocks1, 0)) {
                return TURN_LEFT;
            }
        } else if (myCar.position.lane == 3) {
            if (isObstacle(myCar, blocks2, 0) && isObstacle(myCar, blocks4, 0)) {
                if ((score2 > score4) && (score2 > scoreFront)) {
                    return TURN_LEFT;
                } else if ((score4 > score2) && (score4 > scoreFront)) {
                    return TURN_RIGHT;
                } else if ((scoreFront > score2) && (scoreFront > score4)) {
                    if ((scoreAccelerate >= scoreFront) && (!blocksAccelerate.contains(Terrain.WALL))) {
                        return ACCELERATE;
                    }
                    if ((myCar.speed >= 9) && (hasPowerUp(PowerUps.OIL, myCar.powerups))) {
                        return OIL;
                    }
                    return NOTHING;
                }
            } else if (!isObstacle(myCar, blocks2, 0) && !isObstacle(myCar, blocks4, 0)) {
                if ((score2 >= score4) && (score2 > scoreFront)) {
                    return TURN_LEFT;
                } else if ((score4 > score2) && (score4 > scoreFront)) {
                    return TURN_RIGHT;
                }
            } else if (isObstacle(myCar, blocks2, 0) && !isObstacle(myCar, blocks4, 0)) {
                return TURN_RIGHT;
            } else if (isObstacle(myCar, blocks4, 0) && !isObstacle(myCar, blocks2, 0)) {
                return TURN_LEFT;
            }
        }
        return NOTHING;
    }

    /**
     * Returns map of blocks and the objects in the for the current lanes, returns
     * the amount of blocks that can be
     * traversed at max speed.
     **/
    private List<Object> getBlocksInFront(int lane, int block, GameState gameState) {
        List<Lane[]> map = gameState.lanes;
        List<Object> blocks = new ArrayList<>();
        int startBlock = map.get(0)[0].position.block;

        Lane[] laneList = map.get(lane - 1);
        for (int i = max(block - startBlock, 0); i <= block - startBlock + Bot.maxSpeed; i++) {
            if (laneList[i] == null || laneList[i].terrain == Terrain.FINISH) {
                break;
            }

            if (laneList[i].isOccupiedByCyberTruck) {
                blocks.add(Terrain.WALL);
            } else {
                blocks.add(laneList[i].terrain);
            }
        }
        return blocks;
    }

    private List<Object> getBlocksInFrontCurrentSpeed(int lane, int block, GameState gameState, Car myCar) {
        List<Lane[]> map = gameState.lanes;
        List<Object> blocks = new ArrayList<>();
        int startBlock = map.get(0)[0].position.block;

        Lane[] laneList = map.get(lane - 1);
        for (int i = max(block - startBlock, 0); i <= block - startBlock + myCar.speed; i++) {
            if (laneList[i] == null || laneList[i].terrain == Terrain.FINISH) {
                break;
            }

            if (laneList[i].isOccupiedByCyberTruck) {
                blocks.add(Terrain.WALL);
            } else {
                blocks.add(laneList[i].terrain);
            }

        }
        return blocks;
    }

    private List<Object> getBlocksInFrontAccelerateSpeed(int lane, int block, GameState gameState, Car myCar) {
        List<Lane[]> map = gameState.lanes;
        List<Object> blocks = new ArrayList<>();
        int startBlock = map.get(0)[0].position.block;

        int accelerate = 0;
        if (myCar.speed == 3 || myCar.speed == 6) {
            accelerate = 2;
        } else if (myCar.speed == 5 || myCar.speed == 8) {
            accelerate = 1;
        }

        Lane[] laneList = map.get(lane - 1);
        for (int i = max(block - startBlock, 0); i <= block - startBlock + myCar.speed + accelerate; i++) {
            if (laneList[i] == null || laneList[i].terrain == Terrain.FINISH) {
                break;
            }

            if (laneList[i].isOccupiedByCyberTruck) {
                blocks.add(Terrain.WALL);
            } else {
                blocks.add(laneList[i].terrain);
            }
        }
        return blocks;
    }

    private List<Object> getBlocksInFrontBoost(int lane, int block, GameState gameState) {
        List<Lane[]> map = gameState.lanes;
        List<Object> blocks = new ArrayList<>();
        int startBlock = map.get(0)[0].position.block;

        Lane[] laneList = map.get(lane - 1);
        for (int i = max(block - startBlock, 0); i <= block - startBlock + 15; i++) {
            if (laneList[i] == null || laneList[i].terrain == Terrain.FINISH) {
                break;
            }
            if (laneList[i].isOccupiedByCyberTruck) {
                blocks.add(Terrain.WALL);
            } else {
                blocks.add(laneList[i].terrain);
            }
        }
        return blocks;
    }
}
