package com.textrefinery.domain.port;

public interface NotifierPort {

    void info(String message);

    void error(String message);
}
