package models.robot;

import utils.ANSI;
import utils.Debugger;
import models.virtualmap.OccupancyMap;

import java.io.*;
import java.util.Arrays;
import java.util.StringTokenizer;

// TODO: Write Javadoc for all classes.
// TODO: Change occupancyMap UNKNOWN, EMPTY and OBSTACLE into their getters.

/**
 * Class representing a mobile robot ai.
 *
 * @author Nils Berlijn
 * @author Tom Broenink
 * @version 1.0
 */
public class MobileRobotAI implements Runnable {

    /* Variables */

    /* Directions the mobile robot is facing. */

    /**
     * The north.
     */
    private static final int NORTH = 270;

    /**
     * The east.
     */
    private static final int EAST = 360;

    /**
     * The south.
     */
    private static final int SOUTH = 90;

    /**
     * The west.
     */
    private static final int WEST = 180;

    /* Directions the mobile robot moves to. */

    /**
     * The forward.
     */
    private static final int FORWARD = 0;

    /**
     * The right.
     */
    private static final int RIGHT = 90;

    /**
     * The max view distance.
     */
    private static final int MAX_VIEW_DISTANCE = 10;

    /* Accessories for the mobile robot ai. */

    /**
     * The occupancy map.
     */
    private final OccupancyMap occupancyMap;

    /**
     * The mobile robot.
     */
    private final MobileRobot mobileRobot;

    /**
     * The running.
     */
    private boolean running;

    /* Readers and writers. */

    /**
     * The pipe in.
     */
    PipedInputStream pipeIn;

    /**
     * The input.
     */
    BufferedReader input;

    /**
     * The output.
     */
    PrintWriter output;

    /**
     * The result.
     */
    String result = "";

    /* Positions and measures. */

    /**
     * The position.
     */
    private double[] position;

    /**
     * The measures.
     */
    private double[] measures;

    /**
     * The start x coordinates.
     */
    private double startX;

    /**
     * The start y coordinates.
     */
    private double startY;

    /**
     * The first position.
     */
    private boolean firstPosition;

    /* Constructors */

    /**
     * Mobile robot ai constructor.
     * Creates a new mobile robot ai.
     *
     * @param mobileRobot The mobile robot.
     * @param occupancyMap The occupancy map.
     */
    public MobileRobotAI(MobileRobot mobileRobot, OccupancyMap occupancyMap) {
        // Debugging mode.
        Debugger.debug = false;

        // Debugging.
        Debugger.print("MobileRobotAI", "MobileRobotAI", "executing");

        // Initialize the accessories for the mobile robot ai.
        this.occupancyMap = occupancyMap;
        this.mobileRobot = mobileRobot;

        try {
            // Initialize the readers and writers.
            this.pipeIn = new PipedInputStream();
            this.input = new BufferedReader(new InputStreamReader(pipeIn));
            this.output = new PrintWriter(new PipedOutputStream(pipeIn), true);
            this.result = "";

            // Initialize the positions
            this.position = new double[3];
            this.measures = new double[360];
            this.firstPosition = true;

            // Set running to true.
            this.running = true;

            // Set the output of the mobile robot.
            mobileRobot.setOutput(output);
        } catch (IOException ioException) {
            System.err.println("MobileRobotAI: Something went wrong while initializing.");
        }
    }

    /* Methods */

    /* Process. */

    /**
     * Runs the mobile robot ai.
     */
    public void run() {
        // Debugging.
        Debugger.print("MobileRobotAI", "run", "executing");

        // Keeps running when running is set to true.
        while (running) {
            try {
                // Start processing.
                process();

                // If the map is scanned, quit the mobile robot.
                if (mapScanned()) {
                    mobileRobot.quit();
                }
            } catch (IOException ioException) {
                System.err.println("MobileRobotAI: execution stopped.");
                running = false;
            }
        }

        // Debugging.
        Debugger.print("MobileRobotAI", "run", "finished executing");
    }

    /**
     * The process that the mobile robot passes.
     *
     * @throws IOException
     */
    private void process() throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "process", "executing");

        // Scan the area.
        scanArea();

        // The coordinates of the mobile robot.
        int xCoordinate = (int) this.position[0] / occupancyMap.getCellDimension();
        int yCoordinate = (int) this.position[1] / occupancyMap.getCellDimension();

        // Debugging.
        Debugger.print("MobileRobotAI", "process", "x coordinate: " + xCoordinate);
        Debugger.print("MobileRobotAI", "process", "y coordinate: " + yCoordinate);

        // Search directions.
        int[] searchDirections = determineSearchDirection(FORWARD);

        // Debugging..
        Debugger.print("MobileRobotAI", "process", "searchDirections returns x direction, y direction > " + Arrays.toString(searchDirections));

        // Search direction coordinates.
        int xSearchDirection = searchDirections[0];
        int ySearchDirection = searchDirections[1];

        // If the end is reached.
        boolean reachedEnd = false;

        // The known map.
        char[][] knownMap = this.occupancyMap.getGrid();

        // Amount of steps to take.
        int stepsToTake = 0;

        // Loop till the max view distance has been reached and the reached end is not true.
        while (stepsToTake < MAX_VIEW_DISTANCE && !reachedEnd) {
            // If the right wall is found.
            boolean rightWallFound = searchWallToTheRight(xCoordinate, yCoordinate);

            // If the right wall is found and the known map equals a empty from the occupancy map, increase the steps to take.
            if (rightWallFound && knownMap[xCoordinate][yCoordinate] == OccupancyMap.EMPTY) {
                stepsToTake++;
            // Else if the right wall is found and the known map equals a unknown from the occupancy map, move forward and set the reached end to true.
            } else if (rightWallFound && knownMap[xCoordinate][yCoordinate] == OccupancyMap.UNKNOWN) {
                moveForward(stepsToTake - 2);
                reachedEnd = true;
            // Else if the right wall is found and the known map equals a obstacle from the occupancy map, move forward, rotate to the left and set the reached end to true.
            } else if (rightWallFound && knownMap[xCoordinate][yCoordinate] == OccupancyMap.OBSTACLE) {
                moveForward(stepsToTake - 3);
                rotate("Left");
                reachedEnd = true;
            // Else if the right wall is not found and the known map equals a unknown from the occupancy map, move forward and set the reached end to true.
            } else if (!rightWallFound && knownMap[xCoordinate][yCoordinate] == OccupancyMap.UNKNOWN) {
                moveForward(stepsToTake - 2);
                reachedEnd = true;
            // Else if the right wall is not found and the known map equals a empty from the occupancy map, corner right and set the reached end to true.
            } else if (!rightWallFound && knownMap[xCoordinate][yCoordinate] == OccupancyMap.EMPTY) {
                cornerRight(stepsToTake);
                reachedEnd = true;
            }

            // Increase the coordinates.
            xCoordinate += xSearchDirection;
            yCoordinate += ySearchDirection;
        }

        // If the end is not reached, move forward.
        if (!reachedEnd) {
            moveForward(stepsToTake - 2);
        }
    }

    /**
     * Corner right.
     *
     * @param stepsBeforeCorner The steps before cornet.
     * @throws IOException
     */
    private void cornerRight(int stepsBeforeCorner) throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "cornerRight", "executing");

        // Move forward and scan the area.
        moveForward(stepsBeforeCorner + 3);
        scanArea();

        // The coordinates of the mobile robot.
        int xCoordinate = (int) this.position[0] / occupancyMap.getCellDimension();
        int yCoordinate = (int) this.position[1] / occupancyMap.getCellDimension();

        // If the right wall is found.
        boolean rightWallFound = searchWallToTheRight(xCoordinate, yCoordinate);

        // If the right wall is not found, rotate to the right and move forward.
        if (!rightWallFound) {
            rotate("Right");
            moveForward(2);
        }
    }

    /**
     * Searches a wall to the right.
     *
     * @param xCoordinate The x coordinate.
     * @param yCoordinate The y coordinate.
     * @return If the right wall is found.
     */
    private boolean searchWallToTheRight(int xCoordinate, int yCoordinate) {
        // Debugging.
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "executing");

        // If the right wall is found.
        boolean rightWallFound = true;

        // Debugging.
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "Starting");
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "xCoordinate: " + xCoordinate);
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "yCoordinate: " + yCoordinate);

        // The search directions.
        int[] searchDirections = determineSearchDirection(RIGHT);
        int xSearchDirection = searchDirections[0];
        int ySearchDirection = searchDirections[1];

        // Debugging.
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "xSearchDirection: " + xSearchDirection);
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "ySearchDirection: " + ySearchDirection);

        // The known map.
        char[][] knownMap = this.occupancyMap.getGrid();

        // If the end is reached.
        boolean reachedEnd = false;

        // The steps until the wall.
        int stepsUntilWall = 0;

        // Loop until the steps until the wall are lower than 6 and the reached end is not true.
        while (stepsUntilWall < 6 && !reachedEnd) {
            // If the known map equals a unknown from the occupancy map, set the reached end to true and the right wall found to false.
            if (knownMap[xCoordinate][yCoordinate] == OccupancyMap.UNKNOWN) {
                reachedEnd = true;
                rightWallFound = false;
            // Else If the known map equals an obstacle from the occupancy map, set the reached end to true and the right wall found to true.
            } else if (knownMap[xCoordinate][yCoordinate] == OccupancyMap.OBSTACLE) {
                reachedEnd = true;
                rightWallFound = true;
            }

            // Increase the steps to take.
            stepsUntilWall++;

            // Increase the coordinates.
            xCoordinate += xSearchDirection;
            yCoordinate += ySearchDirection;
        }

        // If the end is not reached, set the right wall found to false.
        if (!reachedEnd) {
            rightWallFound = false;
        }

        // Debugging.
        Debugger.print("MobileRobotAI", "searchWallToTheRight", "returns " + rightWallFound);

        // Return if the right wall is found.
        return rightWallFound;
    }

    /* Commands. */

    /**
     * Rotates the mobile robot.
     *
     * @param direction The direction.
     * @throws IOException
     */
    private void rotate(String direction) throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "rotate", "executing");

        // The command.
        String command;

        // Switch the direction to the left or the right.
        switch (direction) {
            // Case left, set the command to left.
            case "Left":
                command = "LEFT";
                break;
            // Case left, set the command to right.
            case "Right":
                command = "RIGHT";
                break;
            // Default, throw a new illegal argument exception.
            default:
                throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        // Debugging.
        Debugger.print("MobileRobotAI", "rotate", "rotating to the " + command.toLowerCase());

        // Rotate the mobile robot to the given direction.
        mobileRobot.sendCommand("P1.ROTATE" + command.toUpperCase() + " 90");
        result = input.readLine();
    }

    /**
     * Moves the mobile robot forward.
     *
     * @param tiling The tiling.
     * @throws IOException
     */
    private void moveForward(int tiling) throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "moveForward", "executing");
        Debugger.print("MobileRobotAI", "moveForward", "Moving forward " + tiling);

        // Moves the mobile robot forward to the given direction.
        mobileRobot.sendCommand("P1.MOVEFW " + tiling * occupancyMap.getCellDimension());
        result = input.readLine();
    }

    /**
     * The current position of the mobile robot.
     *
     * @throws IOException
     */
    private void currentPosition() throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "currentPosition", "executing");

        // Get the current position of the mobile robot.
        mobileRobot.sendCommand("R1.GETPOS");
        result = input.readLine();
        parsePosition(result, position);
    }

    /**
     * Scans the area.
     *
     * @throws IOException
     */
    private void scanArea() throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "scanArea", "executing");
        Debugger.print("MobileRobotAI", "scanArea", "with laser");

        // Get the current position and scan with the laser.
        currentPosition();
        scan("Laser");

        // If the x position not equals the start x coordinate and the y coordinate not equals the start y coordinate, set the first position to false.
        if (position[0] != startX && position[1] != startY) {
            firstPosition = false;
        }

        // If the first position is not true, set the start x to mobile robot position x and the star y to mobile robot position y.
        if (!firstPosition) {
            startX = mobileRobot.getPlatform().getRobotPosition().getX();
            startY = mobileRobot.getPlatform().getRobotPosition().getY();
        }
    }

    /**
     * Scans.
     *
     * @param with The with.
     * @throws IOException
     */
    private void scan(String with) throws IOException {
        // Debugging.
        Debugger.print("MobileRobotAI", "scan", "executing");

        // The command.
        String command;

        // Switch the with to the laser.
        switch (with) {
            // Case laser, set the command to laser.
            case "Laser":
                command = "L";
                break;
            // Default, throw a new illegal argument exception.
            default:
                throw new IllegalArgumentException("Invalid with: " + with);
        }

        // Scans.
        mobileRobot.sendCommand(command + "1.SCAN");
        result = input.readLine();
        parseMeasures(result, measures);

        // If the command equals laser.
        if (command.equals("L")) {
            occupancyMap.drawLaserScan(position, measures);
        }
    }

    /* Helpers */

    /**
     * Determines the search direction.
     *
     * @param lookingDirection The looking direction.
     * @return The determined search directions.
     */
    private int[] determineSearchDirection(int lookingDirection) {
        // Debugging.
        Debugger.print("MobileRobotAI", "determineSearchDirection", "executing");

        // The current direction.
        int currentDirection = determineClosestDirection(position[2]);

        // If the current direction is 360 and the looking direction is greater than 0.
        if (currentDirection == 360 && lookingDirection > 0) {
            currentDirection = 0;
        }

        // Increase the current direction.
        currentDirection += lookingDirection;

        // The search directions.
        int[] searchDirections = new int[2];

        // Switch the with to the north, east, south or west.
        switch (currentDirection) {
            // Case north, set the x direction to 0 and the y direction to -1.
            case NORTH:
                searchDirections[0] = 0;
                searchDirections[1] = -1;
                break;
            // Case east, set the x direction to 1 and the y direction to 0.
            case EAST:
                searchDirections[0] = 1;
                searchDirections[1] = 0;
                break;
            // Case south, set the x direction to 0 and the y direction to 1.
            case SOUTH:
                searchDirections[0] = 0;
                searchDirections[1] = 1;
                break;
            // Case west, set the x direction to -1 and the y direction to 0.
            case WEST:
                searchDirections[0] = -1;
                searchDirections[1] = 0;
                break;
            // Default, throw a new illegal argument exception.
            default:
                throw new IllegalArgumentException("The currentDirection: " + currentDirection + " is not a known direction.");
        }

        // Debugging.
        Debugger.print("MobileRobotAI", "determineSearchDirection", "xSearchDirection" + searchDirections[0]);
        Debugger.print("MobileRobotAI", "determineSearchDirection", "ySearchDirection" + searchDirections[1]);

        // Return the search directions.
        return searchDirections;
    }

    /**
     *  Determines the closest direction.
     *
     * @param numberToRound The number to round.
     * @return The determined closest direction.
     */
    private int determineClosestDirection(double numberToRound) {
        // Debugging.
        Debugger.print("MobileRobotAI", "determineClosestDirection", "executing");

        // The closest direction.
        int closestDirection;

        // The north, east, south and west.
        int north = (int) (NORTH - numberToRound);
        int east = (int) (EAST - numberToRound);
        int south = (int) (SOUTH - numberToRound);
        int west = (int) (WEST - numberToRound);

        // If north lesser than 2 and north greater than -2, set the closest direction to the north.
        if (north < 2 && north > -2) {
            closestDirection = NORTH;
        // Else if north lesser than 2 and east greater than -2 or east lesser than 362 and east greater than 358, set the closest direction to the east.
        } else if (east < 2 && east > -2 || east < 362 && east > 358) {
            closestDirection = EAST;
        // Else if south lesser than 2 and south greater than -2, set the closest direction to the south.
        } else if (south < 2 && south > -2) {
            closestDirection = SOUTH;
        // Else if west lesser than 2 and west greater than -2, set the closest direction to the west.
        } else if (west < 2 && west > -2) {
            closestDirection = WEST;
        // Else, throw a new illegal argument exception.
        } else {
            throw new IllegalArgumentException("The number provided is outside the predefined boundaries.");
        }

        // Debugging.
        Debugger.print("MobileRobotAI", "determineClosestDirection", "is " + closestDirection);

        // Return the closest direction.
        return closestDirection;
    }

    /**
     * Checks if the map is scanned.
     *
     * @return If the map is scanned.
     */
    private boolean mapScanned() {
        // Debugging.
        Debugger.print("MobileRobotAI", "mapScanned", "executing");

        // If the map is scanned.
        boolean mapScanned = false;

        // The coordinates.
        int xCoordinate = (int) position[0] / occupancyMap.getCellDimension();
        int yCoordinate = (int) position[1] / occupancyMap.getCellDimension();

        // If the wall to the right is found.
        if (searchWallToTheRight(xCoordinate, yCoordinate)) {
            // Set the search directions.
            int[] searchDirections = determineSearchDirection(RIGHT);
            int xSearchDirection = searchDirections[0];
            int ySearchDirection = searchDirections[1];

            // The known map.
            char[][] knownMap = this.occupancyMap.getGrid();

            // While the known map not equals an obstacle from the occupancy map, increase the coordinates with the search direction coordinates.
            while (knownMap[xCoordinate][yCoordinate] != OccupancyMap.OBSTACLE) {
                xCoordinate += xSearchDirection;
                yCoordinate += ySearchDirection;
            }

            // If the search continues.
            boolean continueSearch = true;

            // The start coordinates.
            int startXCoordinate = xCoordinate;
            int startYCoordinate = yCoordinate;

            // The current coordinates.
            int currentXCoordinate = xCoordinate;
            int currentYCoordinate = yCoordinate;

            // The previous coordinates.
            int previousXCoordinate = startXCoordinate;
            int previousYCoordinate = startYCoordinate;

            // Do, while the current x coordinate not equals the start x coordinate or the current y coordinate not equals the start y coordinate and if the search continues is true.
            do {
                try {
                    // The adjacent wall coordinates.
                    int[] adjacentWallCoordinates = searchAdjacentWall(currentXCoordinate, currentYCoordinate, previousXCoordinate, previousYCoordinate);

                    // Set the previous coordinates to the current coordinates.
                    previousXCoordinate = currentXCoordinate;
                    previousYCoordinate = currentYCoordinate;

                    // Set the current coordinates to the adjacent wall coordinates.
                    currentXCoordinate = adjacentWallCoordinates[0];
                    currentYCoordinate = adjacentWallCoordinates[1];
                } catch (IllegalArgumentException illegalArgumentException) {
                    continueSearch = false;
                }
            } while ((currentXCoordinate != startXCoordinate || currentYCoordinate != startYCoordinate) && continueSearch);

            // If the search continues is true, set the map scanned to true.
            if (continueSearch) {
                mapScanned = true;
            }
        }

        // Return if the map is scanned.
        return mapScanned;
    }

    // TODO: Write comments.
    /**
     * Searches the adjacent wall.
     *
     * @param xCoordinate The x coordinate.
     * @param yCoordinate The y coordinate.
     * @param previousXCoordinate The previous x coordinate.
     * @param previousYCoordinate The previous y coordinate.
     * @return The adjacent wall.
     */
    private int[] searchAdjacentWall(int xCoordinate, int yCoordinate, int previousXCoordinate, int previousYCoordinate) {
        // Debugging.
        Debugger.print("MobileRobotAI", "searchAdjacentWall", "executing");

        // The adjacent wall.
        int[] adjacentWall = new int[2];

        // The known map.
        char[][] knownMap = this.occupancyMap.getGrid();

        if (xCoordinate > 0 && knownMap[xCoordinate - 1][yCoordinate] == OccupancyMap.OBSTACLE && (xCoordinate - 1 != previousXCoordinate || yCoordinate != previousYCoordinate)) {
            adjacentWall[0] = xCoordinate - 1;
            adjacentWall[1] = yCoordinate;
        } else if (knownMap[xCoordinate + 1][yCoordinate] == OccupancyMap.OBSTACLE && (xCoordinate + 1 != previousXCoordinate || yCoordinate != previousYCoordinate)) {
            adjacentWall[0] = xCoordinate + 1;
            adjacentWall[1] = yCoordinate;
        } else if (yCoordinate > 0 && knownMap[xCoordinate][yCoordinate - 1] == OccupancyMap.OBSTACLE && (xCoordinate != previousXCoordinate || yCoordinate - 1 != previousYCoordinate)) {
            adjacentWall[0] = xCoordinate;
            adjacentWall[1] = yCoordinate - 1;
        } else if (knownMap[xCoordinate][yCoordinate + 1] == OccupancyMap.OBSTACLE && (xCoordinate != previousXCoordinate || yCoordinate + 1 != previousYCoordinate)) {
            adjacentWall[0] = xCoordinate;
            adjacentWall[1] = yCoordinate + 1;
        // Else, throw a new illegal argument exception.
        } else {
            throw new IllegalArgumentException("Wall is not complete.");
        }

        // Return the adjacent adjacentWall.
        return adjacentWall;
    }

    /* Parsers */

    // TODO: Write comments.
    /**
     *
     *
     * @param value
     * @param position
     */
    private void parsePosition(String value, double position[]) {
        // Debugging.
        Debugger.print("MobileRobotAI", "parsePosition", "executing");

        int indexInit;
        int indexEnd;
        String parameter;

        indexInit = value.indexOf("X=");
        parameter = value.substring(indexInit + 2);
        indexEnd = parameter.indexOf(' ');
        position[0] = Double.parseDouble(parameter.substring(0, indexEnd));

        indexInit = value.indexOf("Y=");
        parameter = value.substring(indexInit + 2);
        indexEnd = parameter.indexOf(' ');
        position[1] = Double.parseDouble(parameter.substring(0, indexEnd));

        indexInit = value.indexOf("DIR=");
        parameter = value.substring(indexInit + 4);
        position[2] = Double.parseDouble(parameter);
    }

    // TODO: Write comments.
    /**
     *
     *
     * @param value
     * @param measures
     */
    private void parseMeasures(String value, double measures[]) {
        // Debugging.
        Debugger.print("MobileRobotAI", "parseMeasures", "executing");

        for (int i = 0; i < 360; i++) {
            measures[i] = 100.0;
        }

        if (value.length() >= 5) {
            System.out.print(ANSI.ANSI_MAGENTA + "Measurements: ");

            value = value.substring(5);
            StringTokenizer tokenizer = new StringTokenizer(value, " ");

            double distance;
            int direction;

            while (tokenizer.hasMoreTokens()) {
                distance = Double.parseDouble(tokenizer.nextToken().substring(2));
                direction = (int) Math.round(Math.toDegrees(Double.parseDouble(tokenizer.nextToken().substring(2))));

                if (direction == 360) {
                    direction = 0;
                }

                measures[direction] = distance;
                System.out.print("direction = " + direction + " distance = " + distance);
            }

            System.out.print("\n");
        }
    }

}
