package edu.berkeley.cs186.database.cli;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.concurrency.LockManager;
import edu.berkeley.cs186.database.memory.ClockEvictionPolicy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * WARNING: We provide the option to run RookieDB as a server for the sole
 * purpose of demonstrating that locking works with multiple clients. We do not
 * recommend trying to expose this server to the open internet, as there is no
 * built-in means of authentication. Even if the data stored in your instance
 * is toy data that you don't mind being leaked, the interface provided allows
 * for almost arbitrary write access to the files in the demo/ directory. This
 * means in the best-case scenario a malicious entity can thrash your disk to
 * their heart's content, and in worse cases populate your disk with
 * malicious files.
 *
 * - Former TA who doesn't want to become a 161 case study
 *
 * To use RookieDB in Server mode, run the main function in this file. This
 * will start a server that waits on port 18600 on your local machine. Then,
 * use a utility like `netcat` or `nc` to open a connection, i.e.:
 * - `netcat localhost 18600`
 * - `nc localhost 18600` (depending on how netcat is installed)
 * - `ncat localhost 18600` (For window users. May need to download first)
 */
public class Server {
    public static final int DEFAULT_PORT = 18600;

    private int port;

    public static void main(String[] args) {
        // Note: you'll probably want to complete Project 4 before
        // attempting to run this.
        Database db = new Database("demo", 25, new LockManager());
        
        // Use the following after completing project 5 (recovery)
        // Database db = new Database("demo", 25, new LockManager(), new ClockEvictionPolicy(), true);
