import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            printError("Missing <database path> and <command>");
            return;
        }

        String databaseFilePath = args[0];
        String command = args[1];

        switch (command) {
            case ".dbinfo" -> getDbInfo(databaseFilePath);
            default -> System.out.println("Missing or invalid command passed: " + command);
        }

    }

    private static void printError(String message) {
        System.out.println("Error: " + message);
    }

    private static boolean isInteriorTable(byte bTreePageType) {
        return bTreePageType == 0x05;
    }

    private static boolean isLeafTable(byte bTreePageType) {
        return bTreePageType == 0x0D;
    }

    private static void getDbInfo(String databaseFilePath) {
        try {
            RandomAccessFile databaseFile = new RandomAccessFile(databaseFilePath, "r");
            databaseFile.seek(16);
            int pageSize = Short.toUnsignedInt(databaseFile.readShort());
            System.out.println("database page size: " + pageSize);
            databaseFile.seek(100);
            int numberOfTables = getNumberOfTables(databaseFile, pageSize, 1);
            System.out.println("number of tables: " + numberOfTables);
        } catch (IOException e) {
           printError("Error reading file: " + e.getMessage());
        }
    }

    private static int getNumberOfTables(RandomAccessFile databaseFile, int pageSize, int pageNumber) {
        try {
            long startPosition = databaseFile.getFilePointer(); // Save the current position
            databaseFile.seek((long) (pageNumber - 1) * pageSize);
            int numberOfTables = 0;
            if (pageNumber == 1) {
                databaseFile.seek(100);
            }
            byte bTreePageType = databaseFile.readByte();
            if (isInteriorTable(bTreePageType)) {
                databaseFile.skipBytes(2); // Skip the page header
                int numberOfCells = Short.toUnsignedInt(databaseFile.readShort());
                databaseFile.skipBytes(3); // Skip the rightmost pointer
                int rightMostPointer = databaseFile.readInt();
                databaseFile.skipBytes(3); // Skip the cell pointer
                for (int i = 0; i < numberOfCells; i++) {
                    int childPageNumber = databaseFile.readInt();
                    numberOfTables += getNumberOfTables(databaseFile, pageSize, childPageNumber);
                    skipVarInt(databaseFile);
                    // Move the pointer forward by the size of the child page number (4 bytes)
                    databaseFile.skipBytes(4);
                }
                numberOfTables += getNumberOfTables(databaseFile, pageSize, rightMostPointer);
            } else if (isLeafTable(bTreePageType)) {
                databaseFile.skipBytes(2); // Skip the page header
                numberOfTables = Short.toUnsignedInt(databaseFile.readShort());
            }
            databaseFile.seek(startPosition); // Return to the original position
            return numberOfTables;
        } catch (IOException e) {
            printError("Error reading file: " + e.getMessage());
            return 0; // or handle it in another way
        }
    }

    private static int skipVarInt(RandomAccessFile file) throws IOException {
        long startPosition = file.getFilePointer();
        int bytesRead = 0;
        byte b;
        // Read bytes until the last byte of the varint
        do {
            b = file.readByte();
            bytesRead++;
        } while ((b & 0x80) != 0);
        file.seek(startPosition + bytesRead);
        return bytesRead; // Return the number of bytes skipped
    }

}
