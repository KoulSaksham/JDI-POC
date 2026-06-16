package com.example.target;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

/**
 * Entry point for the target application. Boots a plain HTTP server
 * exposing /calculate.
 */
public class CalculatorServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/calculate", new RouteHandler());
        server.setExecutor(null);
        server.start();
    }
}
