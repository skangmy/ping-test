package com.skangmy.pingtest;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a simple tool to collect the result of ping test of a targeted IP address.
 * Ping results are stored in a log file
 */
public class Main {
    // create response time pattern
    public static Pattern pattern = Pattern.compile("time=(\\d+ms)");
    public static File logFile;
    public static DateTimeFormatter logEntryTimePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // write log entries in batch
    public static int logBatchSize = 60;
    public static ArrayList<String> logBatch = new ArrayList<>();
    public static boolean verboseMode = false;

    public static void main(String[] args) {
        LocalDateTime testStart = LocalDateTime.now();

        String ip = "localhost";
        if (args.length > 0) {
            try {
                if (args.length >= 2) {
                    ip = args[0];
                    logBatchSize = Integer.parseInt(args[1]);
                    if(args.length == 3) {
                        verboseMode = args[2].equals("--verbose") || args[2].equals("-v");
                    }
                } else {
                    ip = args[0];
                }
            } catch (Exception ex) {
                System.err.println("Error: " + ex);
                printHelp();
            }
        }

        System.out.println("Ping test with " + ip);

        // log file initialization
        String filename = "ping-test-"
                + ip.replaceAll("\\.", "_")
                + "-" + testStart.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".log";

        logFile = new File(filename);

        System.out.println("Ping results are stored in " + filename);

        //The command to execute
        String pingCmd = "ping " + ip + " -t";

        //get the runtime to execute the command
        Runtime runtime = Runtime.getRuntime();

        runtime.addShutdownHook(new Thread(() -> {
            // write the last batch of log entries to file before closing
            System.out.println("Exporting before closing...");
            writeLogBatch();
        }));

        try {
            Process process = runtime.exec(pingCmd);

            //Gets the inputstream to read the output of the command
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));

            //reads the outputs
            String inputLine = in.readLine();
            while ((inputLine != null)) {
                processLine(inputLine);
                inputLine = in.readLine();
            }
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private static void processLine(String inputLine) {
        if (inputLine.length() == 0 || inputLine.startsWith("Pinging"))
            return;

        String record = inputLine;
        if (inputLine.endsWith("unreachable.")) {
            record = "Unreachable";
        } else if (inputLine.contains("time<1ms")) {
            record = "<1ms";
        } else if (inputLine.equals("Request timed out.")) {
            record = "Timed out";
        } else {
            Matcher matcher = pattern.matcher(inputLine);
            if (matcher.find()) {
                // extract the response time from the input line
                record = matcher.group(1);
            }
        }

        if(verboseMode)
            System.out.println(inputLine);

        appendLogBatch(record);
    }

    private static void appendLogBatch(String log) {

        logBatch.add(LocalDateTime.now().format(logEntryTimePattern) + "," + log);

        if(logBatch.size() >= logBatchSize) {
            writeLogBatch();
        }

    }

    private static void writeLogBatch() {
        try {
            PrintWriter writer = new PrintWriter(
                    new FileOutputStream(logFile, true) // to allow log file being appended with new entries
            );

            for (String log : logBatch) {
                writer.println(log);
            }

            writer.close();

            System.out.println(LocalDateTime.now().format(logEntryTimePattern) + " Exported " + logBatch.size() + " records");

            // remove log entries after writing
            logBatch.clear();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar ping-test.jar [ip address] [optional [log batch buffer size] [-v, --verbose]]");
        System.exit(0);
    }

}
