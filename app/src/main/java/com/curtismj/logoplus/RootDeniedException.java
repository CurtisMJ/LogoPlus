package com.curtismj.logoplus;

public class RootDeniedException extends Exception {

    String shellResult;

    public RootDeniedException(String res)
    {
        shellResult = res;
    }

    public String toString() {
        return "Could not obtain root access. Shell result " + shellResult;
    }
}
