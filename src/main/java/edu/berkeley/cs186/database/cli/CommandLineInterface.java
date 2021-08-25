package edu.berkeley.cs186.database.cli;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.cli.parser.ASTSQLStatementList;
import edu.berkeley.cs186.database.cli.parser.ParseException;
import edu.berkeley.cs186.database.cli.parser.RookieParser;
import edu.berkeley.cs186.database.cli.parser.TokenMgrError;
import edu.berkeley.cs186.database.cli.visitor.StatementListVisitor;
import edu.berkeley.cs186.database.concurrency.LockManager;
import edu.berkeley.cs186.database.memory.ClockEvictionPolicy;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.Table;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CommandLineInterface {
    private static final String MASCOT = "\n\\|/  ___------___\n \\__|--%s______%s--|\n    |  %-9s |\n     ---______---\n";
    private static final int[] VERSION = { 1, 8, 6 }; // {major, minor, build}
    private static final String LABEL = "fa21";

    private InputStream in;
    private PrintStream out; // Use instead of System.out to work across a network
    private Database db;
    private Random generator;

    public static void main(String args[]) throws IOException {
        // Basic database for project 0 through 3
        Database db = new Database("demo", 25);
        
        // Use the following after completing project 4 (locking)
        // Database db = new Database("demo", 25, new LockManager());
        
        // Use the following after completing project 5 (recovery)
        // Database db = new Database("demo", 25, new LockManager(), new ClockEvictionPolicy(), true);

        db.loadDemo();

        CommandLineInterface cli = new CommandLineInterface(db);
        cli.run();
        db.close();
    }

    public CommandLineInterface(Database db) {
        // By default, just use standard in and out
        this(db, System.in, System.out);
    }

    public CommandLineInterface(Database db, InputStream in, PrintStream out) {
        this.db = db;
        this.in = in;
        this.out = out;
        this.generator = new Random();
    }

    public void run() {
        // Welcome message
        this.out.printf(MASCOT, "o", "o", institution[this.generator.nextInt(institution.length)]);
        this.out.printf("\nWelcome to RookieDB (v%d.%d.%d-%s)\n", VERSION[0], VERSION[1], VERSION[2], LABEL);

        // REPL
        Transaction currTransaction = null;
        Scanner inputScanner = new Scanner(this.in);
        String input;
        while (true) {
            try {
                input = bufferUserInput(inputScanner);
                if (input.length() == 0)
                    continue;
                if (input.startsWith("\\")) {
                    try {
                        parseMetaCommand(input, db);
                    } catch (Exception e) {
                        this.out.println(e.getMessage());
                    }
                    continue;
                }
                if (input.equals("exit")) {
                    throw new NoSuchElementException();
                }
            } catch (NoSuchElementException e) {
                // User sent termination character
                if (currTransaction != null) {
                    currTransaction.rollback();
                    currTransaction.close();
                }
                this.out.println("exit");
                this.out.println("Bye!"); // If MariaDB says it so can we :)
                return;
            }

            // Convert input to raw bytes
            ByteArrayInputStream stream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            RookieParser parser = new RookieParser(stream);
            ASTSQLStatementList node;
            try {
                node = parser.sql_stmt_list();
            } catch (ParseException | TokenMgrError e) {
                this.out.println("Parser exception: " + e.getMessage());
                continue;
            }
            StatementListVisitor visitor = new StatementListVisitor(db, this.out);
            try {
                node.jjtAccept(visitor, null);
                currTransaction = visitor.execute(currTransaction);
            } catch (DatabaseException e) {
                this.out.println("Database exception: " + e.getMessage());
            }
        }
    }

    public String bufferUserInput(Scanner s) {
        int numSingleQuote = 0;
        this.out.print("=> ");
        StringBuilder result = new StringBuilder();
        boolean firstLine = true;
        do {
            String curr = s.nextLine();
            if (firstLine) {
                String trimmed = curr.trim().replaceAll("(;|\\s)*$", "");
                if (curr.length() == 0) {
                    return "";
                } else if (trimmed.startsWith("\\")) {
                    return trimmed.replaceAll("", "");
                } else if (trimmed.toLowerCase().equals("exit")) {
                    return "exit";
                }
            }
            for (int i = 0; i < curr.length(); i++) {
                if (curr.charAt(i) == '\'') {
                    numSingleQuote++;
                }
            }
            result.append(curr);

            if (numSingleQuote % 2 != 0)
                this.out.print("'> ");
            else if (!curr.trim().endsWith(";"))
                this.out.print("-> ");
            else
                break;
            firstLine = false;
        } while (true);
        return result.toString();
    }

    private void printTable(String tableName) {
        TransactionContext t = TransactionContext.getTransaction();
        Table table = t.getTable(tableName);
        if (table == null) {
            this.out.printf("No table \"%s\" found.", tableName);
            return;
        }
        this.out.printf("Table \"%s\"\n", tableName);
        Schema s = table.getSchema();
        new PrettyPrinter(out).printSchema(s);
    }

    private void parseMetaCommand(String input, Database db) {
        input = input.substring(1); // Shave off the initial slash
        String[] tokens = input.split("\\s+");
        String cmd = tokens[0];
        TransactionContext tc = TransactionContext.getTransaction();
        if (cmd.equals("d")) {
            if (tokens.length == 1) {
                List<Record> records = db.scanTableMetadataRecords();
                new PrettyPrinter(out).printRecords(db.getTableInfoSchema().getFieldNames(),
                        records.iterator());
            } else if (tokens.length == 2) {
                String tableName = tokens[1];
                if (tc == null) {
                    try (Transaction t = db.beginTransaction()) {
                        printTable(tableName);
                    }
                } else {
                    printTable(tableName);
                }
            }
        } else if (cmd.equals("di")) {
            List<Record> records = db.scanIndexMetadataRecords();
            new PrettyPrinter(out).printRecords(db.getIndexInfoSchema().getFieldNames(),
                    records.iterator());
        } else if (cmd.equals("locks")) {
            if (tc == null) {
                this.out.println("No locks held, because not currently in a transaction.");
            } else {
                this.out.println(db.getLockManager().getLocks(tc));
            }
        } else {
            throw new IllegalArgumentException(String.format(
                "`%s` is not a valid metacommand",
                cmd
            ));
        }
    }

    private static String[] institution = {
            "berkeley", "berkley", "berklee", "Brocolli", "BeRKeLEy", "UC Zoom",
            "   UCB  ", "go bears", "   #1  "
    };

    private static List<String> startupMessages = Arrays
            .asList("Speaking with the buffer manager", "Saying grace hash",
                    "Parallelizing parking spaces", "Bulk loading exam preparations",
                    "Declaring functional independence", "Maintaining long distance entity-relationships" );

    private static List<String> startupProblems = Arrays
            .asList("Rebuilding air quality index", "Extinguishing B+ forest fires",
                    "Recovering from PG&E outages", "Disinfecting user inputs", "Shellsorting in-place",
                    "Distributing face masks", "Joining Zoom meetings", "Caching out of the stock market",
                    "Advising transactions to self-isolate", "Tweaking the quarantine optimizer");

    private void startup() {
        Collections.shuffle(startupMessages);
        Collections.shuffle(startupProblems);
        this.out.printf("Starting RookieDB (v%d.%d.%d-%s)\n", VERSION[0], VERSION[1], VERSION[2], LABEL);
        sleep(100);
        for (int i = 0; i < 3; i++) {
            this.out.print(" > " + startupMessages.get(i));
            ellipses();
            sleep(100);
            if (i < 4) {
                this.out.print(" Done");
            } else {
                ellipses();
                this.out.print(" Error!");
                sleep(125);
            }
            sleep(75);
            this.out.println();
        }
        this.out.println("\nEncountered unexpected problems! Applying fixes:");
        sleep(100);
        for (int i = 0; i < 3; i++) {
            this.out.print(" > " + startupProblems.get(i));
            ellipses();
            this.out.print(" Done");
            sleep(75);
            this.out.println();
        }
        sleep(100);
        this.out.println();
        this.out.println("Initialization succeeded!");
        this.out.println();
    }

    private void ellipses() {
        for (int i = 0; i < 3; i++) {
            this.out.print(".");
            sleep(25 + this.generator.nextInt(50));
        }
    }

    private void sleep(int timeMilliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeMilliseconds);
        } catch (InterruptedException e) {
            this.out.println("Interrupt signal received.");
        }
    }
}
