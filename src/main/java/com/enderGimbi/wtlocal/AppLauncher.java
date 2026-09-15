package com.enderGimbi.wtlocal;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AppLauncher {
    public static void main(String[] args){
        Logger.getLogger("com.sun.javafx").setLevel(Level.SEVERE);
        Main.main(args);
    }
}
