package com.mp3bib.backend;

import com.google.gson.Gson;
import com.mp3bib.backend.mp3library.Database;
import com.mp3bib.backend.mp3library.Mp3IO;
import com.mp3bib.communication.BindableBackend;
import com.mp3bib.communication.command.sys_Kill;
import com.mp3bib.logging.CustomLogger;
import com.mp3bib.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Gives an single point of access to request data from the backend via
 * defined request and response types in form of (soon to be) Json-Strings.
 *
 * This class is implemented as an singleton,
 * use the getInstance() instead of a normal constructor call.
 *
 * @author Tizian Rettig - Saphs
 * @version 1.0.1
 */
public class BackendprocessService extends BindableBackend implements Runnable {

    public static final Gson gson = new Gson();
    public Logger logger;
    public Database database;
    private ArrayList<String> requestBuffer;
    private CommandCaller commandCaller;
    private ResponseDistributer responseDistributer;

    private Boolean closeRequest;

    // Singelton implementation ----------------------------------------------------------------------------------------
    private static BackendprocessService instance;

    private BackendprocessService() {
        instance = this;

        logger = new CustomLogger(Logger.LOGLEVEL_INFO);
        logger.debug("New Object of " + getClass().getName() + "instantiated.");

        requestBuffer = new ArrayList<>();
        commandCaller = new CommandCaller();
        responseDistributer = new ResponseDistributer(super.bindables);

        closeRequest = false;
    }

    public static BackendprocessService getInstance() {
        if (instance == null) {
            new BackendprocessService();
        }
        return instance;
    }
    //------------------------------------------------------------------------------------------------------------------


    // Implementation ----------------------------------------------------------------------------------------------------
    @Override
    public void run() {

        logger.info("Backend:\t" + getClass().getTypeName() + " on " + Thread.currentThread().getName() + " starts.");

        synchronized (this) {
            database = new Database();
            try {
                database.clearDB();
                Mp3IO.indirectIndiceDirectories(new File("MusicLibrary.conf"));
            } catch (IOException e) {
                logger.info("MusicLibrary.conf should be in: " + new File("MusicLibrary.conf").getAbsolutePath());
                logger.error("couldn't load MusicLibrary.conf" + e.toString());
            }

            while (!closeRequest) {
                waitForRequest();
                if (!closeRequest) {

                    String currentRequest = requestBuffer.get(0);
                    String response = commandCaller.invoke(currentRequest);

                    responseDistributer.answerAny(response);
                    requestBuffer.remove(0);
                }
            }
        }

        logger.info("Backend:\t" + getClass().getTypeName() + " on " + Thread.currentThread().getName() + " finished.");
    }

    @Override
    public synchronized void pushRequest(String request) {
        requestBuffer.add(request);
        notify();
    }

    @Override
    public Boolean needsToClose() {
        return super.bindables.isEmpty();
    }

    @Override
    public void killBackend() {
        pushRequest(new sys_Kill().createSendable());
    }
    //------------------------------------------------------------------------------------------------------------------

    // Helper methods --------------------------------------------------------------------------------------------------
    private void waitForRequest() {
        while (requestBuffer.isEmpty()) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void giveCloseRequest() {
        closeRequest = true;
    }

    //------------------------------------------------------------------------------------------------------------------
}